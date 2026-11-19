package io.tinychat.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
 * 键：朗读开关与音色、用量行开关、用户元信息开关、当前系统提示词、
 * 用户所在地与语言（喂 `{{location}}` / `{{language}}`）。
 *
 * ⚠️ 刻意**不**把 API Key 放这里：它单独一份加密 DataStore（`credentials`，M7-6）。
 */
class UserSettings(context: Context) {

    private val store = context.applicationContext.settingsDataStore

    val autoSpeak: Flow<Boolean> = store.data.map { it[AUTO_SPEAK] ?: false }

    val ttsVoice: Flow<TtsVoice> = store.data.map { voiceOf(it[TTS_VOICE]) }

    /** 每轮回答下方的用量行（M7-3）；**默认开** */
    val showUsage: Flow<Boolean> = store.data.map { it[SHOW_USAGE] ?: true }

    /** 用户元信息（`latest_reminder`）开关（M7-5）；默认开，与官方一致 */
    val reminderEnabled: Flow<Boolean> = store.data.map { it[REMINDER] ?: true }

    /** 当前选中的系统提示词（M7.5）；内置 id 或自定义预设 id */
    val promptPresetId: Flow<String> = store.data.map { it[PROMPT_PRESET] ?: "default" }

    /** 用户所在地，喂给 `{{location}}` 与 `latest_reminder`；空 = 不填 */
    val promptLocation: Flow<String> = store.data.map { it[PROMPT_LOCATION] ?: "" }

    /** 用户常用语言，喂给 `{{language}}` 与 `latest_reminder` */
    val promptLanguage: Flow<String> = store.data.map { it[PROMPT_LANGUAGE] ?: "中文" }

    /** 外观：`system` / `light` / `dark`（M8-4） */
    val themeMode: Flow<String> = store.data.map { it[THEME_MODE] ?: "system" }

    /** 字体大小：1.0–1.3，只作用于正文（M8-4） */
    val fontScale: Flow<Float> = store.data.map { it[FONT_SCALE] ?: 1f }

    suspend fun setAutoSpeak(enabled: Boolean) {
        store.edit { it[AUTO_SPEAK] = enabled }
    }

    suspend fun setTtsVoice(voice: TtsVoice) {
        store.edit { it[TTS_VOICE] = voice.wire }
    }

    suspend fun setShowUsage(enabled: Boolean) {
        store.edit { it[SHOW_USAGE] = enabled }
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        store.edit { it[REMINDER] = enabled }
    }

    suspend fun setPromptPresetId(id: String) {
        store.edit { it[PROMPT_PRESET] = id }
    }

    suspend fun setPromptLocation(location: String) {
        store.edit { it[PROMPT_LOCATION] = location.trim() }
    }

    suspend fun setPromptLanguage(language: String) {
        store.edit { it[PROMPT_LANGUAGE] = language }
    }

    suspend fun setThemeMode(mode: String) {
        store.edit { it[THEME_MODE] = mode }
    }

    suspend fun setFontScale(scale: Float) {
        store.edit { it[FONT_SCALE] = scale.coerceIn(0.9f, 1.3f) }
    }

    companion object {
        private val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        private val TTS_VOICE = stringPreferencesKey("tts_voice")
        private val SHOW_USAGE = booleanPreferencesKey("show_usage")
        private val REMINDER = booleanPreferencesKey("reminder_enabled")
        private val PROMPT_PRESET = stringPreferencesKey("prompt_preset_id")
        private val PROMPT_LOCATION = stringPreferencesKey("prompt_location")
        private val PROMPT_LANGUAGE = stringPreferencesKey("prompt_language")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val FONT_SCALE = floatPreferencesKey("font_scale")

        /** 认不出的值一律退回默认音色（旧的/手改的文件不该把朗读弄挂）。 */
        internal fun voiceOf(raw: String?): TtsVoice =
            TtsVoice.entries.firstOrNull { it.wire == raw } ?: TtsVoice.STELLA
    }
}
