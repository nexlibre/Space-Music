package com.music.bitchord.data

import org.junit.Assert.*
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class AppUpdateCheckerTest {
    @Test fun stableSupersedesBetaAndNumericVersionsCompareCorrectly() {
        assertTrue(AppUpdateChecker.isNewer("2.1.0", "2.0.1-beta"))
        assertTrue(AppUpdateChecker.isNewer("2.1.0", "2.1.0-beta"))
        assertTrue(AppUpdateChecker.isNewer("2.10.0", "2.9.0"))
        assertFalse(AppUpdateChecker.isNewer("2.1.0", "2.1.0"))
        assertFalse(AppUpdateChecker.isNewer("2.0.1", "2.1.0"))
        assertFalse(AppUpdateChecker.isNewer("2.1.0-beta", "2.1.0"))
    }
    @Test fun noticesOnlyQuerySpaceMusic() {
        assertEquals("https://api.github.com/repos/nexlibre/Space-Music/releases/latest", AppUpdateChecker.LATEST_RELEASE_URL)
    }
    @Test fun spatialChannelSelectsOnlyStableSpatialTags() {
        val releases = """[
            {"tag_name":"v2.3.0","draft":false,"prerelease":false},
            {"tag_name":"spatial-v1.2.0","draft":false,"prerelease":true},
            {"tag_name":"spatial-v1.0.0","draft":false,"prerelease":false}
        ]"""
        val selected = AppUpdateChecker.selectSpatialRelease(releases)
        assertEquals("spatial-v1.0.0", selected?.get("tag_name")?.jsonPrimitive?.contentOrNull)
    }

    @Test fun spatialChannelIgnoresStableReleasesFromTheOriginalApp() {
        val selected = AppUpdateChecker.selectSpatialRelease("""[{"tag_name":"v3.0.0","draft":false,"prerelease":false}]""")
        assertNull(selected)
    }

}
