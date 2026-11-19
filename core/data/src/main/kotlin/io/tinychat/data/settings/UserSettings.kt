package io.tinychat.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.tinychat.model.TtsVoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 设置库：整个 app 只有这一份，DataStore 不允许同一个文件建两个实例。 */
private val Context.settingsDataStore by preferencesDataStore(name = "user_settings")

/**
 * 本地设置（M5 起用它，M8 的外观/字号也走这里）。
 *
 * 现在只有两个键：
 * - `auto_speak`：顶栏那个喇叭开关，开着就自动朗读每条新回答；
 * - `tts_voice`：朗读音色，只有官方两个（决策 3）。
 *
 * ⚠️ 刻意**不**把 API Key 放这里：Key 目前来自 `BuildConfig`，M8-6 会换成
 * Keystore + DataStore，到那时再在这个文件里加加密存取。
 */
class UserSettings(context: Context) {

    private val store = context.applicationContext.settingsDataStore

    val autoSpeak: Flow<Boolean> = store.data.map { it[AUTO_SPEAK] ?: false }

    val ttsVoice: Flow<TtsVoice> = store.data.map { voiceOf(it[TTS_VOICE]) }

    suspend fun setAutoSpeak(enabled: Boolean) {
        store.edit { it[AUTO_SPEAK] = enabled }
    }

    suspend fun setTtsVoice(voice: TtsVoice) {
        store.edit { it[TTS_VOICE] = voice.wire }
    }

    companion object {
        private val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        private val TTS_VOICE = stringPreferencesKey("tts_voice")

        /** 认不出的值一律退回默认音色（旧的/手改的文件不该把朗读弄挂）。 */
        internal fun voiceOf(raw: String?): TtsVoice =
            TtsVoice.entries.firstOrNull { it.wire == raw } ?: TtsVoice.STELLA
    }
}
