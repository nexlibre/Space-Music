package com.music.bitchord.playback

/** Immutable control snapshot. No Android dependency; safe to publish to the audio thread. */
enum class SpatialMode { ORIGINAL, EIGHT_D, ADVANCED }

data class SpatialParameters(
    val intensity: Float = .55f,
    val speed: Float = .5f,
    val width: Float = .6f,
    val depth: Float = .5f,
    val distance: Float = .35f,
    val reverb: Int = 1,
    val profile: Int = 1,
) {
    fun sanitized() = copy(intensity = intensity.safe(), speed = speed.safe(), width = width.safe(),
        depth = depth.safe(), distance = distance.safe(), reverb = reverb.coerceIn(0, 2), profile = profile.coerceIn(-1, 2))
    /** 0% = 40 s/revolution; 100% = 6 s/revolution. */
    val periodSeconds: Float get() = 40f - 34f * speed.safe()
    companion object {
        fun preset(mode: SpatialMode, index: Int): SpatialParameters {
            val i = index.coerceIn(0, 2)
            return if (mode == SpatialMode.EIGHT_D) SpatialParameters(
                intensity = floatArrayOf(.3f, .55f, .8f)[i], speed = floatArrayOf(.25f, .5f, .65f)[i],
                width = floatArrayOf(.35f, .6f, .85f)[i], reverb = if (i == 2) 2 else 1, profile = i)
            else SpatialParameters(intensity = floatArrayOf(.3f, .55f, .75f)[i],
                width = floatArrayOf(.35f, .6f, .85f)[i], depth = floatArrayOf(.25f, .5f, .75f)[i],
                distance = floatArrayOf(.15f, .35f, .6f)[i], reverb = if (i == 2) 2 else 1, profile = i)
        }
    }
}
private fun Float.safe(): Float = if (isFinite()) coerceIn(0f, 1f) else .5f

data class SpatialSettings(
    val mode: SpatialMode = SpatialMode.ORIGINAL,
    val eightD: SpatialParameters = SpatialParameters.preset(SpatialMode.EIGHT_D, 1),
    val advanced: SpatialParameters = SpatialParameters.preset(SpatialMode.ADVANCED, 1),
    val visualization: Boolean = false,
) {
    val selected: SpatialParameters get() = if (mode == SpatialMode.ADVANCED) advanced else eightD
    fun withSelected(value: SpatialParameters): SpatialSettings = if (mode == SpatialMode.ADVANCED)
        copy(advanced = value.sanitized()) else copy(eightD = value.sanitized())
}
