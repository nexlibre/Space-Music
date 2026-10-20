package com.music.bitchord.playback

import kotlin.math.*

/** Parametric binaural approximation, not a measured HRTF or source separator.
 * Audio-thread owned, bounded delay memory; no per-frame allocations or platform effects.
 * All mode weights and parameters settle exponentially (30 ms time constant).
 */
class SpatialSceneEngine {
    var left = 0f; private set
    var right = 0f; private set
    private var rate = 48000
    private var smooth = 1f / 1440f
    private var phase = 0.0
    private var originalWeight = 0f
    private var eightWeight = 0f
    private var advancedWeight = 0f
    private var amount8 = .55f; private var speed = .5f; private var width8 = .6f
    private var amountA = .55f; private var widthA = .6f; private var depth = .5f; private var distance = .35f
    private var rev8 = 0f; private var revA = 0f
    private var historyL = FloatArray(1); private var historyR = FloatArray(1)
    private var roomL = FloatArray(1); private var roomR = FloatArray(1)
    private var index = 0; private var roomIndexL = 0; private var roomIndexR = 0
    private var roomFilteredL = 0f; private var roomFilteredR = 0f
    private var roomCoefficient = .2f
    private var parked = false
    private var farL = 0f; private var farR = 0f
    val active: Boolean get() = originalWeight + eightWeight + advancedWeight > .000001f

    fun configure(sampleRate: Int) {
        rate = sampleRate.coerceIn(8000, 384000)
        smooth = (1.0 - exp(-1.0 / (.03 * rate))).toFloat()
        roomCoefficient = (1.0 - exp(-2.0 * PI * 1800.0 / rate)).toFloat()
        historyL = FloatArray((rate * .001).toInt() + 3); historyR = FloatArray(historyL.size)
        roomL = FloatArray((rate * .037).toInt()); roomR = FloatArray((rate * .043).toInt())
        reset()
    }
    fun reset() {
        clearHistories()
        parked = false
        index = 0; roomIndexL = 0; roomIndexR = 0; farL = 0f; farR = 0f; phase = 0.0
        originalWeight = 0f; eightWeight = 0f; advancedWeight = 0f
    }
    private fun clearHistories() {
        historyL.fill(0f); historyR.fill(0f); roomL.fill(0f); roomR.fill(0f)
        farL = 0f; farR = 0f
        roomFilteredL = 0f; roomFilteredR = 0f
    }
    fun release() {
        historyL = FloatArray(1); historyR = FloatArray(1)
        roomL = FloatArray(1); roomR = FloatArray(1)
        reset()
    }
    private fun park() { if (!parked) { clearHistories(); parked = true } }
    private fun approach(value: Float, target: Float) = if (abs(target - value) < .001f) target else value + smooth * (target - value)
    private fun delayed(buffer: FloatArray, samples: Float): Float {
        var position = index - samples
        if (position < 0) position += buffer.size
        val i = position.toInt()
        val f = position - i
        return buffer[i] * (1f - f) + buffer[(i + 1) % buffer.size] * f
    }
    fun process(l: Float, r: Float, originalL: Float, originalR: Float, enabled: Boolean, settings: SpatialSettings) {
        originalWeight = approach(originalWeight, if (enabled && settings.mode == SpatialMode.ORIGINAL) 1f else 0f)
        eightWeight = approach(eightWeight, if (enabled && settings.mode == SpatialMode.EIGHT_D) 1f else 0f)
        advancedWeight = approach(advancedWeight, if (enabled && settings.mode == SpatialMode.ADVANCED) 1f else 0f)
        // Exact bypass once tails have settled; no residual rounding changes to dry PCM.
        if (!enabled && !active) { park(); left = l; right = r; return }
        if (originalWeight == 1f && eightWeight == 0f && advancedWeight == 0f) {
            park(); left = originalL; right = originalR; return
        }
        parked = false
        val a = settings.eightD; val b = settings.advanced
        amount8 = approach(amount8, a.intensity); speed = approach(speed, a.speed); width8 = approach(width8, a.width)
        amountA = approach(amountA, b.intensity); widthA = approach(widthA, b.width)
        depth = approach(depth, b.depth); distance = approach(distance, b.distance)
        rev8 = approach(rev8, a.reverb * .08f); revA = approach(revA, b.reverb * .08f)
        val dryL = if (l.isFinite()) l else 0f; val dryR = if (r.isFinite()) r else 0f
        historyL[index] = dryL; historyR[index] = dryR
        // Opposite-ear path: 0.15–0.65 ms ITD + level difference + head-shadow lowpass.
        val delay = rate * (.00015f + .0005f * widthA)
        val cutoff = 1600f + 3000f * (1f - depth)
        val coefficient = (1.0 - exp(-2.0 * PI * cutoff / rate)).toFloat()
        farL += coefficient * (delayed(historyR, delay) - farL)
        farR += coefficient * (delayed(historyL, delay) - farR)
        val cross = .38f * (1f - .55f * widthA)
        val attenuation = 1f - .25f * distance
        val binauralL = (dryL * (1f - cross) + farL * cross) * attenuation
        val binauralR = (dryR * (1f - cross) + farR * cross) * attenuation
        // Elliptical orbit: bounded level motion + front/back depth, retaining both stereo channels.
        val azimuth = sin(phase).toFloat()
        val frontBack = cos(phase).toFloat()
        val orbitDepth = 1f - .12f * width8 * (1f - frontBack)
        val orbitL = dryL * (1f - .42f * width8 * (1f + azimuth)) * orbitDepth
        val orbitR = dryR * (1f - .42f * width8 * (1f - azimuth)) * orbitDepth
        phase += 2.0 * PI / (rate * (40.0 - 34.0 * speed))
        if (phase >= 2.0 * PI) phase -= 2.0 * PI
        val echoL = roomL[roomIndexL]; val echoR = roomR[roomIndexR]
        roomL[roomIndexL] = dryL * .65f + echoR * .35f
        roomR[roomIndexR] = dryR * .65f + echoL * .35f
        // Damp the early reflection edges instead of introducing a discrete delayed step.
        roomFilteredL += roomCoefficient * (echoL - roomFilteredL)
        roomFilteredR += roomCoefficient * (echoR - roomFilteredR)
        val eightL = dryL + amount8 * ((orbitL * (1f - rev8) + roomFilteredL * rev8) - dryL)
        val eightR = dryR + amount8 * ((orbitR * (1f - rev8) + roomFilteredR * rev8) - dryR)
        val advancedL = dryL + amountA * ((binauralL * (1f - revA) + roomFilteredL * revA) - dryL)
        val advancedR = dryR + amountA * ((binauralR * (1f - revA) + roomFilteredR * revA) - dryR)
        val dryWeight = (1f - originalWeight - eightWeight - advancedWeight).coerceAtLeast(0f)
        // Original route retains its historical headroom; new routes are convex combinations.
        left = dryL * dryWeight + originalL * originalWeight + eightL * eightWeight + advancedL * advancedWeight
        right = dryR * dryWeight + originalR * originalWeight + eightR * eightWeight + advancedR * advancedWeight
        index = (index + 1) % historyL.size
        roomIndexL = (roomIndexL + 1) % roomL.size; roomIndexR = (roomIndexR + 1) % roomR.size
    }
}
