package io.tinychat.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile

/**
 * 录音（M5-1）：16kHz / 16bit / 单声道 PCM，直接写成 ASR 能吃的 WAV。
 *
 * 几个刻意的选择：
 *
 * - 采样率固定 16kHz：ASR 只认 8k/16k/22.05k/24k/32k/44.1k/48k，16k 体积小、识别够用；
 * - 用 `VOICE_RECOGNITION` 音源（关掉 AGC 与部分处理，识别更准），拿不到再退回 `MIC`；
 * - 先写 44 字节占位头，录完按实际长度**回填**（长度对不上 ASR 会拒收）；
 * - 上限 120s 由录音线程自己刹车并回调，不在外面靠计时器抢（计时器会慢半拍）。
 *
 * 录音在独立线程跑，[Listener] 的回调也在那个线程上，别在里面碰 View。
 */
class VoiceRecorder(
    private val sampleRate: Int = SAMPLE_RATE,
    private val maxSeconds: Int = MAX_SECONDS,
) {

    fun interface Listener {
        /** 每约 64ms 一帧 PCM，给频谱用（数组会被复用，别存引用）。 */
        fun onPcm(samples: ShortArray)
    }

    /** 录满 [maxSeconds] 时回调（在录音线程上）。 */
    var onLimitReached: (() -> Unit)? = null

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var output: RandomAccessFile? = null
    private var target: File? = null

    @Volatile
    private var running = false

    private var dataBytes = 0

    val isRecording: Boolean get() = running

    /**
     * 开始录音。[target] 不存在会创建父目录。
     *
     * @throws SecurityException 没给 `RECORD_AUDIO`
     * @throws IllegalStateException 麦克风被占用 / 设备不支持
     */
    fun start(target: File, listener: Listener? = null) {
        check(!running) { "already recording" }
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()

        val audioRecord = createRecord()
        val file = RandomAccessFile(target, "rw")
        try {
            // 先占 44 字节头的位置，录完再回填长度
            file.write(ByteArray(WavHeader.SIZE))
            audioRecord.startRecording()
        } catch (error: Throwable) {
            runCatching { file.close() }
            runCatching { audioRecord.release() }
            throw error
        }

        record = audioRecord
        output = file
        this.target = target
        dataBytes = 0
        running = true

        val limitBytes = maxSeconds * sampleRate * 2
        thread = Thread({
            val frame = ShortArray(FRAME_SAMPLES)
            val bytes = ByteArray(FRAME_SAMPLES * 2)
            try {
                while (running) {
                    val read = audioRecord.read(frame, 0, frame.size)
                    if (read <= 0) {
                        // 负数 = 读失败（设备被抢走），0 = 暂时没数据
                        if (read < 0) break else continue
                    }
                    writeFrame(bytes, frame, read)
                    if (dataBytes >= limitBytes) {
                        running = false
                        onLimitReached?.invoke()
                        break
                    }
                    if (read == frame.size) {
                        listener?.onPcm(frame)
                    } else {
                        listener?.onPcm(frame.copyOf(read))
                    }
                }
            } catch (_: Throwable) {
                running = false
            }
        }, "tc-voice-recorder").apply {
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
    }

    /**
     * 结束录音并回填 WAV 头。
     *
     * @return 录音文件；一帧都没录到（点一下就松手）返回 null 并删掉空文件。
     */
    fun stop(): RecordedAudio? {
        if (!running && record == null) return null
        running = false
        thread?.join(2_000)
        thread = null

        val audioRecord = record
        record = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }

        val file = output
        output = null
        val bytes = dataBytes
        val path = target
        target = null

        if (file == null || path == null) return null
        try {
            // 回填长度：RIFF 与 data 两处
            val header = ByteArray(WavHeader.SIZE)
            WavHeader.write(header, sampleRate, bytes)
            file.seek(0)
            file.write(header)
        } finally {
            runCatching { file.close() }
        }

        if (bytes <= 0) {
            path.delete()
            return null
        }
        return RecordedAudio(path, bytes, durationMs = bytes / 2L * 1000L / sampleRate)
    }

    /** 放弃本次录音（切后台、取消、权限被收回）。 */
    fun cancel() {
        val path = target
        val audioRecord = record
        running = false
        thread?.join(2_000)
        thread = null
        record = null
        target = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        runCatching { output?.close() }
        output = null
        dataBytes = 0
        path?.delete()
    }

    private fun writeFrame(bytes: ByteArray, frame: ShortArray, count: Int) {
        var offset = 0
        for (index in 0 until count) {
            val value = frame[index].toInt()
            bytes[offset++] = value.toByte()
            bytes[offset++] = (value shr 8).toByte()
        }
        runCatching { output?.write(bytes, 0, count * 2) }
        dataBytes += count * 2
    }

    // 录音权限由调用方（VoiceController）在进语音模式前申请；这里再兜一层 try/catch，
    // 被拒时 build() 抛 SecurityException 会落到下面的 lastError —— lint 看不到这条链路，
    // 所以显式压掉 MissingPermission，别让它淹掉真正的问题。
    @SuppressLint("MissingPermission")
    private fun createRecord(): AudioRecord {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBuffer > 0) { "unsupported audio config" }
        val bufferSize = minBuffer * 2

        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
        var lastError: Throwable? = null
        for (source in sources) {
            try {
                val record = AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build(),
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .build()
                if (record.state == AudioRecord.STATE_INITIALIZED) return record
                record.release()
                lastError = IllegalStateException("AudioRecord not initialized (source=$source)")
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("no microphone")
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_SECONDS = 120

        /** 一帧约 64ms；频谱刷新够用，也不算费电。 */
        private const val FRAME_SAMPLES = 1024
    }
}

/** 一次录音的结果。 */
data class RecordedAudio(
    val file: File,
    val bytes: Int,
    val durationMs: Long,
)
