package com.music.bitchord

import android.os.Debug
import android.util.Log
import com.music.bitchord.playback.*
import com.music.bitchord.playback.audio.AudioBlock
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

/** Emulator CPU diagnostics, not a physical-device battery or perceptual test. */
class SpatialRuntimeTest {
    @Test fun realtimeBudgetAndMonoBypass() {
        val processor = SpatialAudioProcessor()
        val block = AudioBlock(2, 1024); block.reset(1024)
        val input = FloatArray(2048) { (sin(it.toDouble() * .04) * .4).toFloat() }
        processor.configure(48000, 2)
        for (mode in listOf<SpatialMode?>(null, SpatialMode.ORIGINAL, SpatialMode.EIGHT_D, SpatialMode.ADVANCED)) {
            processor.enabled = mode != null
            processor.settings = SpatialSettings(mode = mode ?: SpatialMode.ORIGINAL)
            repeat(50) { input.copyInto(block.samples); processor.process(block) }
            val before = Debug.threadCpuTimeNanos()
            val heapBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            repeat(469) {
                input.copyInto(block.samples); processor.process(block)
                assertTrue(block.samples.all { it.isFinite() })
            }
            val cpuMs = (Debug.threadCpuTimeNanos() - before) / 1e6
            val heapAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            Log.i("SpaceSpatialBenchmark", "mode=$mode frames=480256 rate=48000 cpuMs=$cpuMs heapBefore=$heapBefore heapAfter=$heapAfter")
            assertTrue("DSP exhausted 25% of realtime CPU budget: $mode $cpuMs ms", cpuMs < 2500.0)
        }
        val mono = AudioBlock(1, 32); mono.reset(32); mono.samples.fill(.25f)
        processor.configure(48000, 1); processor.process(mono)
        assertTrue(mono.samples.all { it == .25f })
        processor.reset()
    }
}
