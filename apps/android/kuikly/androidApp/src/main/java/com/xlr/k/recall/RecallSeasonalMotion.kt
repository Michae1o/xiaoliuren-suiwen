package com.xlr.k.recall

import kotlin.math.abs

internal fun unit(x: Float) = x.coerceIn(0f, 1f)
internal fun mix(a: Float, b: Float, x: Float) = a + (b - a) * x
internal fun night(camera: Float) = 1f - abs(camera.coerceIn(0f, 2f) - 1f)

internal fun cubicProgress(t: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    if (t <= 0f || t >= 1f) return unit(t)
    fun b(u: Float, a: Float, c: Float): Float {
        val m = 1f - u
        return 3f * m * m * u * a + 3f * m * u * u * c + u * u * u
    }
    var low = 0f
    var high = 1f
    repeat(14) {
        val mid = (low + high) * .5f
        if (b(mid, x1, x2) < t) low = mid else high = mid
    }
    return b((low + high) * .5f, y1, y2)
}

internal const val EQUINOX_SECONDS = 3.05f
internal const val EQUINOX_BASE = 0x101B2A
internal data class EquinoxWash(val x: Float, val y: Float, val rx: Float, val ry: Float, val rgb: Int, val alpha: Float)

internal val equinoxWashes = listOf(
    EquinoxWash(.22f, .69f, .65f, .55f, 0xC28253, .46f),
    EquinoxWash(.76f, .27f, .65f, .55f, 0x7198B7, .53f),
    EquinoxWash(.22f, .70f, .32f, .32f, 0xE7AD77, .17f),
    EquinoxWash(.77f, .27f, .30f, .29f, 0xA9C9D8, .16f),
)
internal val equinoxStops = floatArrayOf(0f, .18f, .46f, .74f, 1f)
internal val equinoxFalloff = floatArrayOf(1f, .90f, .59f, .22f, 0f)

internal fun equinoxProgress(age: Float, reduced: Boolean): Float {
    if (reduced) return 1f
    val t = unit(age / EQUINOX_SECONDS)
    return t * t * (3f - 2f * t)
}

internal data class FrostCut(
    val name: String, val ratio: Float, val lag: Float, val weight: Float,
    val maskX: Float, val maskY: Float, val maskRx: Float, val maskRy: Float,
    val radialStops: FloatArray, val sideStops: FloatArray? = null, val softness: Float,
)
internal val frostCuts = listOf(
    FrostCut("frost-side", 235f / 531, 0f, .85f, 0f, .52f, .99f, .49f,
        floatArrayOf(0f, 1f, .19f, 1f, .55f, 11f / 15, .78f, 5f / 15, 1f, 0f), softness = .21f),
    FrostCut("frost-sill", 614f / 215, .12f, .88f, .33f, 1f, .68f, 1.05f,
        floatArrayOf(0f, 1f, .26f, 1f, .53f, 11f / 15, .79f, 4f / 15, 1f, 0f),
        floatArrayOf(0f, 0f, .12f, 1f, .77f, 1f, 1f, 0f), .19f),
    FrostCut("frost-corner", 595f / 136, .24f, .78f, .64f, 1f, .76f, 1.13f,
        floatArrayOf(0f, 1f, .30f, 1f, .57f, 11f / 15, 1f, 0f),
        floatArrayOf(0f, 0f, .23f, 1f, .83f, 1f, 1f, 0f), .20f),
)
internal data class FrostPose(val x: Float, val y: Float, val w: Float, val h: Float, val photoAlpha: Float, val grow: Float)
internal fun frostPose(cut: FrostCut, width: Float, height: Float, age: Float, camera: Float, reduced: Boolean): FrostPose {
    val compact = width <= 690f || width <= height
    val ice = if (reduced) 1f else cubicProgress(age / 3.14f, .32f, .16f, .41f, 1f)
    val growth = unit((ice - cut.lag) / (1f - cut.lag))
    val w = when (cut.name) {
        "frost-side" -> if (compact) (width * .16f).coerceIn(50f, 79f) else (width * .09f).coerceIn(63f, 116f)
        "frost-sill" -> if (compact) (width * .59f).coerceIn(148f, 258f) else (width * .48f).coerceIn(208f, 568f)
        else -> if (compact) (width * .47f).coerceIn(111f, 199f) else (width * .39f).coerceIn(150f, 490f)
    }
    val h = w / cut.ratio
    val x = when (cut.name) {
        "frost-side" -> width * if (compact) -.029f else -.017f
        "frost-sill" -> width * if (compact) -.01f else .03f
        else -> width * (1f + if (compact) .04f else .023f) - w
    }
    val y = when (cut.name) {
        "frost-side" -> height * if (compact) .34f else .27f
        "frost-sill" -> height * (1f + if (compact) .017f else .028f) - h
        else -> height * (1f + if (compact) .009f else .018f) - h
    }
    return FrostPose(x, y, w, h, (mix(.03f, .47f, growth) - .15f * night(camera) * growth) * cut.weight,
        mix(-.18f, 1.03f, growth))
}
