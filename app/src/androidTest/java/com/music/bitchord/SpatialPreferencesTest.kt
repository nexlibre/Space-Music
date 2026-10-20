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

    @Test fun legacyAndCorruptedPreferencesRecoverWithoutLosingTheEnableFlag() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = instrumentation.targetContext.getSharedPreferences("bitchord_settings", 0)
        instrumentation.runOnMainSync {
            val previous = prefs.getString("spatial_scene_v1", null)
            val enabled = AppSettings.spatialAudio.value
            try {
                prefs.edit().remove("spatial_scene_v1").putBoolean("spatial_audio", true).commit()
                AppSettings.reload()
                assertEquals(SpatialSettings(), AppSettings.spatialScene.value)
                assertTrue(AppSettings.spatialAudio.value)
                for (invalid in listOf("{broken-json", "[]")) {
                    prefs.edit().putString("spatial_scene_v1", invalid).commit()
                    AppSettings.reload()
                    assertEquals(SpatialSettings(), AppSettings.spatialScene.value)
                    assertTrue(AppSettings.spatialAudio.value)
                }
                prefs.edit().putInt("spatial_scene_v1", 7).commit()
                AppSettings.reload()
                assertEquals(SpatialSettings(), AppSettings.spatialScene.value)
                assertTrue(AppSettings.spatialAudio.value)
            } finally {
                val edit = prefs.edit().putBoolean("spatial_audio", enabled)
                if (previous == null) edit.remove("spatial_scene_v1") else edit.putString("spatial_scene_v1", previous)
                edit.commit()
                AppSettings.reload()
            }
        }
    }
}
