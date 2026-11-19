package io.tinychat.feature.chat.voice

import android.content.Context
import io.tinychat.audio.PlaybackStopped
import io.tinychat.audio.RecordedAudio
import io.tinychat.audio.SpectrumAnalyser
import io.tinychat.audio.TtsPlayer
import io.tinychat.audio.VoiceRecorder
import io.tinychat.model.TtsVoice
import io.tinychat.net.speech.SpeechClient
import io.tinychat.net.speech.SpeechException
import io.tinychat.net.speech.SpeechFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 语音的"设备侧"聚合：录音、频谱、ASR、TTS 播放（M5）。
 *
 * 这里只做**单次动作**，状态机在 `ChatViewModel` 里：这样录音线程、播放线程
 * 与 Compose 状态之间只有一条数据流（[events]），不会互相等。
 */
class VoiceController(
    context: Context,
    private val speech: SpeechClient = SpeechClient(),
) {

    private val appContext = context.applicationContext
    private val recorder = VoiceRecorder()

    /** 录音时的柱高；UI 按宽度调 [SpectrumAnalyser.resize]。 */
    val spectrum = SpectrumAnalyser(bands = 32)

    /** 播放器线程/广播回调转成事件（都在非主线程发出）。 */
    val events = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 16)

    private var player: TtsPlayer? = null

    val isRecording: Boolean get() = recorder.isRecording

    fun startRecording() {
        spectrum.reset()
        val file = File(appContext.cacheDir, "voice/recording-${System.currentTimeMillis()}.wav")
        recorder.onLimitReached = { events.tryEmit(VoiceEvent.RecordingLimitReached) }
        recorder.start(file) { samples -> spectrum.update(samples) }
    }

    fun stopRecording(): RecordedAudio? = recorder.stop().also { spectrum.reset() }

    fun cancelRecording() {
        recorder.cancel()
        spectrum.reset()
    }

    /** 上传识别。[RecordedAudio] 的临时文件在结束后删掉（成功失败都删）。 */
    suspend fun transcribe(apiKey: String, audio: RecordedAudio): String =
        try {
            speech.transcribe(apiKey, audio.file).text.trim()
        } finally {
            audio.file.delete()
        }

    /**
     * 朗读一段文本：按句切段、串行合成、边下边播。
     *
     * 阻塞到播完（或在 [stopPlayback] 时抛 [io.tinychat.audio.PlaybackStopped]）。
     */
    suspend fun play(apiKey: String, text: String, voice: TtsVoice) {
        val track = TtsPlayer(appContext).also { player = it }
        track.onFinished = { events.tryEmit(VoiceEvent.PlaybackFinished) }
        track.onPaused = { events.tryEmit(VoiceEvent.PlaybackPaused) }
        track.onNoisy = { events.tryEmit(VoiceEvent.PlaybackNoisy) }

        val segments = TtsTextChunker.split(text)
        if (segments.isEmpty()) throw SpeechException(SpeechFailure.NO_AUDIO, "没有可朗读的文本")

        try {
            if (!track.start()) throw SpeechException(SpeechFailure.UNKNOWN, "拿不到音频焦点")
            for (segment in segments) {
                // 已经按播放速度限速了：write 会阻塞到缓冲有空位
                speech.synthesize(apiKey, segment, voice) { chunk -> track.write(chunk) }
            }
            withContext(Dispatchers.IO) { track.drainAndRelease() }
        } catch (error: PlaybackStopped) {
            // 用户点停止 / 切会话 / 拔耳机：这是**正常结束**，别当失败抛出去
            runCatching { track.stop() }
        } catch (error: CancellationException) {
            runCatching { track.stop() }
            throw error
        } finally {
            if (player === track) player = null
        }
    }

    fun stopPlayback() {
        player?.stop()
        player = null
    }

    fun pausePlayback() {
        player?.pause()
    }

    fun resumePlayback() {
        player?.resume()
    }
}

/** 语音侧异步事件。 */
sealed interface VoiceEvent {
    /** 录满 120s 自动停（录音已经结束，该走"识别并发送"了）。 */
    data object RecordingLimitReached : VoiceEvent

    /** 播完了。 */
    data object PlaybackFinished : VoiceEvent

    /** 来电/别人抢焦点：已暂停。 */
    data object PlaybackPaused : VoiceEvent

    /** 拔耳机：已停止。 */
    data object PlaybackNoisy : VoiceEvent
}
