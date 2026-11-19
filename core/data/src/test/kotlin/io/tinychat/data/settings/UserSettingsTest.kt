package io.tinychat.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.tinychat.model.TtsVoice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserSettingsTest {

    private lateinit var settings: UserSettings

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        settings = UserSettings(context)
    }

    @Test
    fun `默认不自动朗读、音色是海星`() = runTest {
        assertFalse(settings.autoSpeak.first())
        assertEquals(TtsVoice.STELLA, settings.ttsVoice.first())
    }

    @Test
    fun `改了能读回来`() = runTest {
        settings.setAutoSpeak(true)
        settings.setTtsVoice(TtsVoice.TIDE)
        assertTrue(settings.autoSpeak.first())
        assertEquals(TtsVoice.TIDE, settings.ttsVoice.first())
    }

    @Test
    fun `认不出的音色值退回默认`() {
        assertEquals(TtsVoice.STELLA, UserSettings.voiceOf(null))
        assertEquals(TtsVoice.STELLA, UserSettings.voiceOf("bogus"))
        assertEquals(TtsVoice.STELLA, UserSettings.voiceOf("stella"))
        assertEquals(TtsVoice.TIDE, UserSettings.voiceOf("tide"))
    }
}
