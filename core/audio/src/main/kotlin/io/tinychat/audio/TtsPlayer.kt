package io.tinychat.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * TTS 播放（M5-4）：把服务端边合成边下发的裸 PCM 直接灌进 `AudioTrack`。
 *
 * 为什么不用 `MediaPlayer` / ExoPlayer：
 *
 * - 服务端 `format=wav` 返回的头里 `data` 长度是**流式占位**（`0x7FFFFFFF`），落文件播放会算错时长；
 * - `format=pcm` 是 24kHz/16bit/mono 的裸流，实测首字节约 1s、约 6 倍速产出，
 *   边收边写就能「1 秒出声」，没必要等整段。
 *
 * 线程模型：[write] 在调用方线程（网络协程）里跑，用**非阻塞写 + 小睡**做背压，
 * 这样暂停（来电）时不会卡死在 `write` 里；[stop] 可从任意线程调用。
 */
class TtsPlayer(context: Context) {

    /** 播放结束 / 被外部停掉 / 出错，调用方据此收尾 UI。 */
    var onFinished: (() -> Unit)? = null

    /** 来电或别的应用抢走焦点：已自动暂停，等 [resume]。 */
    var onPaused: (() -> Unit)? = null

    /** 拔耳机：已停止，不会自动恢复。 */
    var onNoisy: (() -> Unit)? = null

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private var track: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    private var noisyReceiver: BroadcastReceiver? = null

    @Volatile
    private var stopped = false

    @Volatile
    private var paused = false

    @Volatile
    private var totalFrames = 0L

    private var remainder: Byte? = null

    val isPlaying: Boolean get() = track != null && !stopped

    val isPaused: Boolean get() = paused

    /** 拿焦点 + 建轨；返回 false 表示没拿到焦点（通话中等），调用方应放弃播放。 */
    fun start(): Boolean {
        release()
        stopped = false
        paused = false
        totalFrames = 0
        remainder = null

        if (!requestFocus()) {
            stopped = true
            return false
        }

        val bufferBytes = runCatching {
            AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        }.getOrDefault(0).let { if (it > 0) it * 4 else SAMPLE_RATE * 2 / 4 }

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = audioTrack
        audioTrack.play()
        registerNoisyReceiver()
        return true
    }

    /**
     * 写一段 PCM（长度可以是奇数，内部会留一个字节到下次）。
     *
     * 缓冲满了就小睡等播放线程消费，所以它天然按播放速度限速；
     * 被 [stop] 打断时抛 [PlaybackStopped]。
     */
    fun write(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val audioTrack = track ?: throw PlaybackStopped()

        // PCM 是 16 位对齐的，网络分片的边界可能切在半个采样上：留一个字节到下次
        val buffered: ByteArray = if (remainder != null) {
            ByteArray(bytes.size + 1).also {
                it[0] = remainder!!
                bytes.copyInto(it, destinationOffset = 1)
            }
        } else {
            bytes
        }
        remainder = null
        val count = buffered.size - (buffered.size % 2)
        if (count < buffered.size) remainder = buffered[count]

        var offset = 0
        while (offset < count) {
            if (stopped) throw PlaybackStopped()
            if (paused) {
                Thread.sleep(20)
                continue
            }
            val written = audioTrack.write(
                buffered,
                offset,
                count - offset,
                AudioTrack.WRITE_NON_BLOCKING,
            )
            if (written > 0) {
                offset += written
                totalFrames += written / 2
            } else {
                Thread.sleep(10)
            }
        }
    }

    /**
     * 数据写完了，等缓冲里的声音放完再释放。
     *
     * 会阻塞到播完；被 [stop] 打断则立即返回。
     */
    fun drainAndRelease() {
        val audioTrack = track
        if (audioTrack != null) {
            var guard = 0
            while (!stopped && !paused && guard < DRAIN_TIMEOUT_MS / 20) {
                val head = runCatching { audioTrack.playbackHeadPosition.toLong() and 0xFFFFFFFFL }.getOrDefault(0L)
                if (head >= totalFrames) break
                Thread.sleep(20)
                guard++
            }
        }
        val finished = !stopped
        release()
        if (finished) onFinished?.invoke()
    }

    fun pause() {
        val audioTrack = track ?: return
        if (paused) return
        paused = true
        runCatching { audioTrack.pause() }
    }

    fun resume() {
        val audioTrack = track ?: return
        if (!paused) return
        paused = false
        runCatching { audioTrack.play() }
    }

    /** 立即停止（用户点停止、切会话、开始新的朗读）。 */
    fun stop() {
        val wasPlaying = track != null && !stopped
        stopped = true
        release()
        if (wasPlaying) onFinished?.invoke()
    }

    private fun release() {
        unregisterNoisyReceiver()
        track?.let { audioTrack ->
            runCatching { audioTrack.pause() }
            runCatching { audioTrack.flush() }
            runCatching { audioTrack.stop() }
            runCatching { audioTrack.release() }
        }
        track = null
        totalFrames = 0
        if (hasFocus) {
            hasFocus = false
            runCatching {
                focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            }
            focusRequest = null
        }
    }

    private fun requestFocus(): Boolean {
        val manager = audioManager ?: return true
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    -> {
                        pause()
                        onPaused?.invoke()
                    }

                    AudioManager.AUDIOFOCUS_GAIN -> resume()
                    // 让我们主动压低音量：朗读内容不需要，忽略
                    else -> Unit
                }
            }
            .build()
        focusRequest = request
        hasFocus = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    stop()
                    onNoisy?.invoke()
                }
            }
        }
        runCatching {
            appContext.registerReceiver(
                receiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            )
        }
        noisyReceiver = receiver
    }

    private fun unregisterNoisyReceiver() {
        noisyReceiver?.let { receiver -> runCatching { appContext.unregisterReceiver(receiver) } }
        noisyReceiver = null
    }

    companion object {
        /** 服务端 `format=pcm` 的固定参数：24kHz / 16bit / 单声道。 */
        const val SAMPLE_RATE = 24_000

        private const val DRAIN_TIMEOUT_MS = 30_000L
    }
}

/** 播放被停止：`write` / `drainAndRelease` 正常退出的信号，不算错误。 */
class PlaybackStopped : Exception()
