package com.music.bitchord

import com.music.bitchord.playback.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import java.util.Random

class SpatialSceneEngineTest {
    @Test fun parametersAreFiniteAndBounded() {
        val p = SpatialParameters(Float.NaN, Float.POSITIVE_INFINITY, -1f, 2f, Float.NEGATIVE_INFINITY, 20, 30).sanitized()
        assertEquals(.5f, p.intensity, 0f); assertEquals(0f, p.width, 0f)
        assertEquals(1f, p.depth, 0f); assertEquals(2, p.reverb)
        assertEquals(40f, p.copy(speed = 0f).periodSeconds, 0f)
        assertEquals(6f, p.copy(speed = 1f).periodSeconds, 0f)
    }
    @Test fun changingAndResettingOneModePreservesTheOther() {
        val saved = SpatialSettings(mode = SpatialMode.ADVANCED, eightD = SpatialParameters(speed = .12f))
        val changed = saved.withSelected(SpatialParameters.preset(SpatialMode.ADVANCED, 2))
        assertEquals(saved.eightD, changed.eightD)
        assertEquals(2, changed.advanced.profile)
        assertEquals(SpatialMode.ORIGINAL, SpatialSettings().mode)
    }
    @Test fun originalIsExactAfterTransitionAndDisableReturnsExactDry() {
        val engine = SpatialSceneEngine(); engine.configure(48000)
        repeat(48000) { engine.process(.3f, -.2f, .7f, -.5f, true, SpatialSettings()) }
        assertEquals(.7f, engine.left, 0f); assertEquals(-.5f, engine.right, 0f)
        repeat(48000) { engine.process(.3f, -.2f, .7f, -.5f, false, SpatialSettings()) }
        assertEquals(.3f, engine.left, 0f); assertEquals(-.2f, engine.right, 0f)
        assertFalse(engine.active)
    }
    @Test fun signalsAndRepeatedModeChangesRemainFiniteWithoutAddedClipping() {
        val random = Random(11)
        for (rate in listOf(8000, 44100, 48000, 96000, 192000)) {
            val engine = SpatialSceneEngine(); engine.configure(rate)
            var peak = 0f
            val start = System.nanoTime()
            repeat(rate * 2) { i ->
                val l = when ((i / 3000) % 4) {
                    0 -> sin(2 * PI * 440 * i / rate).toFloat()
                    1 -> random.nextFloat() * 2f - 1f
                    2 -> if (i % 1000 == 0) 1f else 0f
                    else -> .7f
                }
                val r = if (i % 2 == 0) -l else l
                val mode = if ((i / 791) % 2 == 0) SpatialMode.EIGHT_D else SpatialMode.ADVANCED
                val settings = SpatialSettings(mode = mode,
                    eightD = SpatialParameters(intensity = 1f, width = 1f, reverb = 2),
                    advanced = SpatialParameters(intensity = 1f, width = 1f, depth = 1f, reverb = 2))
                engine.process(l, r, l, r, i % 1399 != 0, settings)
                assertTrue(engine.left.isFinite() && engine.right.isFinite())
                peak = max(peak, max(abs(engine.left), abs(engine.right)))
            }
            assertTrue("Added clipping at $rate Hz: $peak", peak <= 1.00001f)
            println("Spatial DSP $rate Hz, 2 s stereo: wallMs=${(System.nanoTime()-start)/1e6}, peak=$peak (JVM diagnostic, not Android battery benchmark)")
        }
    }
    @Test fun smoothControlsAndSwitchesDoNotStepOnConstantInput() {
        val engine = SpatialSceneEngine(); engine.configure(48000)
        var previous = 0f; var maxStep = 0f; var maxStepFrame = -1
        repeat(96000) { i ->
            val mode = SpatialMode.entries[(i / 24000) % 3]
            val p = SpatialParameters(intensity = if (i % 1000 < 500) 0f else 1f, width = 1f, reverb = 2)
            engine.process(.5f, .5f, .41f, .41f, true, SpatialSettings(mode, p, p))
            if (i > 10000 && abs(engine.left - previous) > maxStep) {
                maxStep = abs(engine.left - previous); maxStepFrame = i
            }
            previous = engine.left
        }
        assertTrue("Discontinuity $maxStep at frame $maxStepFrame", maxStep < .01f)
    }
    @Test fun parkedRoutesDoNotReplayStaleReverb() {
        val engine = SpatialSceneEngine(); engine.configure(48000)
        val orbit = SpatialSettings(mode = SpatialMode.EIGHT_D)
        repeat(48000) { engine.process(.5f, .5f, .5f, .5f, true, orbit) }
        repeat(48000) { engine.process(0f, 0f, 0f, 0f, false, orbit) }
        repeat(10000) {
            engine.process(0f, 0f, 0f, 0f, true, orbit)
            assertEquals(0f, engine.left, 0f); assertEquals(0f, engine.right, 0f)
        }
        engine.release()
    }
    @Test fun reenabledOriginalDoesNotReplayHistoryFromBeforeBypass() {
        val processor = SpatialAudioProcessor()
        processor.configure(48000, 2)
        val block = com.music.bitchord.playback.audio.AudioBlock(2, 512)
        block.reset(512)
        fun feed(left: Float) {
            for (i in 0 until block.samples.size step 2) {
                block.samples[i] = left; block.samples[i + 1] = 0f
            }
            processor.process(block)
        }
        processor.enabled = true
        repeat(100) { feed(1f) }
        processor.enabled = false
        repeat(100) { feed(1f) }
        repeat(100) { feed(0f) }
        processor.enabled = true
        repeat(4) {
            feed(0f)
            assertTrue("Original replayed dormant crossfeed", block.samples.all { it == 0f })
        }
        processor.reset()
    }
    @Test fun binauralImpulseContainsDelayedFilteredContralateralEnergy() {
        val engine = SpatialSceneEngine(); engine.configure(48000)
        val settings = SpatialSettings(mode = SpatialMode.ADVANCED, advanced = SpatialParameters(intensity = 1f, reverb = 0))
        repeat(48000) { engine.process(0f, 0f, 0f, 0f, true, settings) }
        var firstOpposite = -1; var energy = 0f
        repeat(100) { i ->
            engine.process(if (i == 0) 1f else 0f, 0f, 0f, 0f, true, settings)
            if (abs(engine.right) > 1e-6f && firstOpposite == -1) firstOpposite = i
            energy += engine.right * engine.right
        }
        assertTrue("Missing ITD: $firstOpposite", firstOpposite in 7..32)
        assertTrue(energy > 0f && energy < 1f)
    }

    @Test fun everyModeWorksWithEqTransitionAndActiveNormalization() {
        val chain = com.music.bitchord.playback.audio.DspChain()
        chain.configure(48000, 2)
        chain.spatial.enabled = true
        chain.equalizer.setTuning(true, EqCurve(FloatArray(EqLayout.SLOTS), FloatArray(EqLayout.SLOTS) { .707f }, preampDb = 3f), 0f)
        chain.transition.open()
        chain.loudness.gainFor = { .8f }
        chain.loudness.track("spatial-integration-test", null)
        val block = com.music.bitchord.playback.audio.AudioBlock(2, 512)
        block.reset(512)
        for (mode in SpatialMode.entries) {
            chain.spatial.settings = SpatialSettings(mode = mode)
            repeat(120) { chunk ->
                for (frame in 0 until block.frameCount) {
                    val value = (.8 * sin(2 * PI * 440 * (chunk * 512 + frame) / 48000)).toFloat()
                    block.samples[frame * 2] = value
                    block.samples[frame * 2 + 1] = -value
                }
                chain.process(block)
                assertTrue("Non-finite or unbounded output with $mode + EQ + normalization",
                    block.samples.all { it.isFinite() && abs(it) <= 1.00001f })
            }
        }
        chain.reset()
    }
}
