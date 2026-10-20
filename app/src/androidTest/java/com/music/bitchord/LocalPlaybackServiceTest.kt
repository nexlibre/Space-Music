package com.music.bitchord

import android.content.ComponentName
import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.PlaybackService
import com.music.bitchord.playback.playSongs
import com.music.bitchord.playback.toSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LocalPlaybackServiceTest {
    @Test fun largeLocalQueueSurvivesPlaybackAndActivityRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val audio = File(context.filesDir, "service-${UUID.randomUUID()}.wav")
        val dataSize = 44100 * 2 * 30
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(44100).putInt(88200).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(dataSize)
        audio.writeBytes(buffer.array())
        grantLocalPermissions()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var controller: MediaController? = null
        try {
            var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
            instrumentation.runOnMainSync {
                future = MediaController.Builder(context,
                    SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
            }
            val player = future!!.get(20, TimeUnit.SECONDS)
            controller = player
            val started = CountDownLatch(1)
            val error = AtomicReference<PlaybackException?>()
            instrumentation.runOnMainSync {
                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) started.countDown() }
                    override fun onPlayerError(e: PlaybackException) { error.set(e); started.countDown() }
                })
            }
            val songs = (0 until 731).map { index ->
                Song("local-service-$index", "Local $index", "Test", null,
                    localUri = Uri.fromFile(audio).toString(), localPath = audio.absolutePath)
            }
            runBlocking { withContext(Dispatchers.Main) { player.playSongs(songs, 365) } }
            assertTrue("Service did not start local audio", started.await(20, TimeUnit.SECONDS))
            assertNull("Local service playback failed", error.get())
            instrumentation.runOnMainSync {
                com.music.bitchord.playback.PlayerDeepLink.consume(android.content.Intent()
                    .putExtra(com.music.bitchord.playback.PlayerDeepLink.EXTRA_OPEN_PLAYER, true))
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(1000)
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(context.getExternalFilesDir(null), "playback-integration.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            scenario.recreate()
            instrumentation.runOnMainSync {
                assertEquals("local-service-365", player.currentMediaItem!!.mediaId)
                assertEquals(731 - 365, player.mediaItemCount - player.currentMediaItemIndex)
                assertEquals("local-service-730", player.getMediaItemAt(player.mediaItemCount - 1).mediaId)
                player.pause()
                player.seekToNextMediaItem()
                player.play()
            }
            scenario.recreate()
            instrumentation.runOnMainSync {
                assertEquals("local-service-366", player.currentMediaItem!!.mediaId)
                assertEquals(731 - 366, player.mediaItemCount - player.currentMediaItemIndex)
                assertEquals("local-service-730", player.getMediaItemAt(player.mediaItemCount - 1).mediaId)
                assertNull(player.playerError)
            }
        } finally {
            instrumentation.runOnMainSync { controller?.stop(); controller?.clearMediaItems(); controller?.release() }
            scenario.close()
            audio.delete()
        }
    }

    private fun grantLocalPermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        // UiAutomation.grantRuntimePermission was added after API 26.
        // The shell command is available throughout our supported API range.
        val permissions = if (android.os.Build.VERSION.SDK_INT >= 33) {
            listOf(android.Manifest.permission.READ_MEDIA_AUDIO, android.Manifest.permission.POST_NOTIFICATIONS)
        } else listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        permissions.forEach { permission ->
            instrumentation.uiAutomation.executeShellCommand("pm grant $packageName $permission").use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            }
            assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED, instrumentation.targetContext.checkSelfPermission(permission))
        }
    }

    @Test fun commonLocalFormatsPlayThroughTheService() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantLocalPermissions()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var controller: MediaController? = null
        val files = mutableListOf<File>()
        val savedScene = com.music.bitchord.data.settings.AppSettings.spatialScene.value
        val savedSpatial = com.music.bitchord.data.settings.AppSettings.spatialAudio.value
        try {
            var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
            instrumentation.runOnMainSync {
                future = MediaController.Builder(context,
                    SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
            }
            val player = future!!.get(20, TimeUnit.SECONDS)
            controller = player
            for (extension in listOf("mp3", "flac", "m4a", "ogg")) {
                val audio = File(context.filesDir, "codec-${UUID.randomUUID()}.$extension")
                files.add(audio)
                val encoded = instrumentation.context.assets.open("spatial-tone.$extension.b64").use { it.readBytes() }
                audio.writeBytes(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT))
                val started = CountDownLatch(1)
                val error = AtomicReference<PlaybackException?>()
                val expectedId = "local-codec-$extension"
                val discontinuities = mutableListOf<Int>()
                var checkingTransition = false
                val listener = object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        if (player.currentMediaItem?.mediaId == expectedId && player.isPlaying &&
                            player.playbackState == Player.STATE_READY) started.countDown()
                    }
                    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                        if (checkingTransition) discontinuities.add(reason)
                    }
                    override fun onPlayerError(e: PlaybackException) { error.set(e); started.countDown() }
                }
                instrumentation.runOnMainSync { player.stop(); player.addListener(listener) }
                val song = Song("local-codec-$extension", "Local $extension", "Test", null,
                    localUri = Uri.fromFile(audio).toString(), localPath = audio.absolutePath)
                runBlocking { withContext(Dispatchers.Main) { player.playSongs(listOf(song), 0) } }
                assertTrue("$extension did not start", started.await(20, TimeUnit.SECONDS))
                assertNull("$extension playback failed", error.get())
                // isPlaying can arrive before the remote controller's position clock has
                // settled. Require actual progress on the intended item before testing DSP.
                val clockReady = CountDownLatch(1)
                val deadline = android.os.SystemClock.elapsedRealtime() + 5000
                while (clockReady.count > 0 && android.os.SystemClock.elapsedRealtime() < deadline) {
                    instrumentation.runOnMainSync {
                        if (player.currentMediaItem?.mediaId == expectedId && player.isPlaying &&
                            player.currentPosition >= 1000) clockReady.countDown()
                    }
                    if (clockReady.count > 0) Thread.sleep(50)
                }
                assertEquals("$extension output clock never advanced on $expectedId", 0L, clockReady.count)
                for (mode in com.music.bitchord.playback.SpatialMode.entries) {
                    var before = 0L
                    instrumentation.runOnMainSync {
                        assertEquals(expectedId, player.currentMediaItem?.mediaId)
                        before = player.currentPosition
                        discontinuities.clear()
                        checkingTransition = true
                        com.music.bitchord.data.settings.AppSettings.setSpatialScene(
                            com.music.bitchord.data.settings.AppSettings.spatialScene.value.copy(mode = mode))
                        com.music.bitchord.data.settings.AppSettings.setSpatialAudio(true)
                    }
                    Thread.sleep(350)
                    instrumentation.runOnMainSync {
                        assertNull("$extension/$mode", player.playerError)
                        assertTrue("$extension/$mode stopped", player.isPlaying)
                        val after = player.currentPosition
                        android.util.Log.i("SpaceSpatialContinuity", "$extension/$mode before=$before after=$after reasons=$discontinuities")
                        assertEquals(expectedId, player.currentMediaItem?.mediaId)
                        assertTrue("$extension/$mode discontinuity: $discontinuities", discontinuities.isEmpty())
                        // Remote controller extrapolation may be corrected by a few ms.
                        // A restart from the >=1 s baseline cannot pass this 50 ms bound.
                        assertTrue("$extension/$mode position regressed: $before -> $after", after + 50 >= before)
                        checkingTransition = false
                    }
                }
                instrumentation.runOnMainSync {
                    com.music.bitchord.data.settings.AppSettings.setSpatialAudio(false)
                    assertTrue("$extension has no decoded duration", player.duration > 0)
                    player.pause()
                    player.removeListener(listener)
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                com.music.bitchord.data.settings.AppSettings.setSpatialScene(savedScene)
                com.music.bitchord.data.settings.AppSettings.setSpatialAudio(savedSpatial)
                controller?.stop(); controller?.clearMediaItems(); controller?.release()
            }
            scenario.close()
            files.forEach { it.delete() }
        }
    }

    @Test fun unreadableFileLeavesAnErrorAndNextSelectionStillPlays() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantLocalPermissions()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var controller: MediaController? = null
        val missing = File(context.filesDir, "missing-${UUID.randomUUID()}.mp3")
        val good = File(context.filesDir, "recovery-${UUID.randomUUID()}.mp3")
        try {
            val encoded = instrumentation.context.assets.open("tone.mp3.b64").use { it.readBytes() }
            good.writeBytes(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT))
            var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
            instrumentation.runOnMainSync {
                future = MediaController.Builder(context,
                    SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
            }
            val player = future!!.get(20, TimeUnit.SECONDS)
            controller = player
            val failed = CountDownLatch(1)
            val resumed = CountDownLatch(1)
            instrumentation.runOnMainSync {
                player.addListener(object : Player.Listener {
                    override fun onPlayerError(e: PlaybackException) { failed.countDown() }
                    override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) resumed.countDown() }
                })
            }
            val missingUri = Uri.fromFile(missing).toString()
            val bad = Song("local-missing", "Missing local file", "Test", null,
                localUri = missingUri, localPath = missing.absolutePath)
            runBlocking { withContext(Dispatchers.Main) { player.playSongs(listOf(bad), 0) } }
            assertTrue("Missing file did not report an error", failed.await(20, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                assertNotNull(player.playerError)
                assertEquals(missingUri, player.currentMediaItem!!.toSong().localUri)
                assertFalse(player.playWhenReady)
            }
            scenario.recreate()
            val next = Song("local-recovery", "Readable local file", "Test", null,
                localUri = Uri.fromFile(good).toString(), localPath = good.absolutePath)
            runBlocking { withContext(Dispatchers.Main) { player.playSongs(listOf(next), 0) } }
            assertTrue("Playback did not recover after selecting a valid file", resumed.await(20, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { assertNull(player.playerError) }
        } finally {
            instrumentation.runOnMainSync { controller?.stop(); controller?.clearMediaItems(); controller?.release() }
            scenario.close()
            good.delete()
        }
    }
}
