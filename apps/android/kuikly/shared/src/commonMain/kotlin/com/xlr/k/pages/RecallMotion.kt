package com.xlr.k.pages

import kotlin.math.PI
import kotlin.math.sin

/** Pure ambient motion: no random numbers per frame, strictly cosmetic. */
internal data class RecallPulse(val period: Float, val phase: Float) {
    fun at(seconds: Float): Float = recallWave(seconds, period, phase)
}

internal fun recallWave(time: Float, period: Float, phase: Float = 0f): Float =
    sin((time / period + phase) * 2 * PI).toFloat()

internal fun recallSceneClock(elapsed: Float, reduced: Boolean) = if (reduced) 0f else elapsed

internal fun recallPulse(seed: Int, minimumPeriod: Float = 5.4f, span: Float = 5.90f): RecallPulse {
    val hash = (seed * 1103515245 + 12345).ushr(9)
    return RecallPulse(minimumPeriod + span * ((hash % 591) / 590f), ((hash / 591) % 1009) / 1009f)
}

internal fun recallFlareEnvelope(pulse: RecallPulse, seconds: Float): Float {
    val wave = .5f + .5f * pulse.at(seconds)
    return wave * wave * wave
}

internal fun recallLocalDrift(seed: Int, seconds: Float): Pair<Float, Float> =
    (8f * recallPulse(seed, 17f, 11f).at(seconds)) to
        (5.8f * recallPulse(seed + 103, 18f, 13f).at(seconds))
