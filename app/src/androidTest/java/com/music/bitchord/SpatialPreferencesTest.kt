package com.music.bitchord

import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.*
import org.junit.Assert.*
import org.junit.Test

class SpatialPreferencesTest {
    @Test fun independentSpatialControlsSurviveReloadWithoutChangingLegacyToggle() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val old = AppSettings.spatialScene.value
            val enabled = AppSettings.spatialAudio.value
            try {
                val settings = SpatialSettings(SpatialMode.ADVANCED,
                    SpatialParameters(speed = .18f, reverb = 0, profile = -1),
                    SpatialParameters(depth = .91f, distance = .72f, profile = 2), visualization = true)
                AppSettings.setSpatialScene(settings)
                AppSettings.reload()
                assertEquals(settings, AppSettings.spatialScene.value)
                assertEquals(enabled, AppSettings.spatialAudio.value)
            } finally { AppSettings.setSpatialScene(old) }
        }
    }
}
