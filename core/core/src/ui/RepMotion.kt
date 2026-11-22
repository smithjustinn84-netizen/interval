package dev.justinsmith.interval.ui

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** [progress] brought into a rep `[0, 1)`, so a moment before its start is one near the end of the rep before. */
internal fun looped(progress: Float): Float =
    if (progress >= 0f && progress < 1f) progress else ((progress % 1f) + 1f) % 1f

/** [to] moved by whole turns to the one nearest this angle: a joint gets there the short way round. */
fun Float.nearestTurn(to: Float): Float = to + ((this - to) / Angles.FULL_TURN).roundToInt() * Angles.FULL_TURN

/**
 * The breath the figure is at [progress] through the rep: from -1 (breathed out) to 1 (breathed in). Takes a whole
 * number of breaths per rep so the breath loops seamlessly, and runs continuously out of a set-up into the rep.
 */
fun ExerciseRep.breathAt(progress: Float): Float {
    val breaths = if (progress < 0f) {
        progress * (setUp?.seconds ?: 0f) / BREATH_SECONDS
    } else {
        progress * (seconds / BREATH_SECONDS).roundToInt().coerceAtLeast(1)
    }
    return sin(breaths * TURN).toFloat()
}

/**
 * How fresh the drive is at [progress]: 1 at the moment a stretch of [RepBeat.Drive] starts, easing quadratically
 * to 0 over [PULSE_SECONDS], and 0 everywhere else (including set-up).
 */
fun ExerciseRep.pulseAt(progress: Float): Float {
    if (progress < 0f && setUp != null) return 0f
    val at = if (progress == 1f) 1f else looped(progress)
    val span = spanAt(at) ?: return 0f
    if (span.beat != RepBeat.Drive) return 0f
    val left = 1f - (at - span.from) * seconds / PULSE_SECONDS
    return if (left <= 0f) 0f else left * left
}

/**
 * The place [share] of the way from [from] to [to] in a rep, taken the short way round the loop (never through a
 * set-up when both endpoints are in the rep).
 */
fun placeBetween(from: Float, to: Float, share: Float): Float {
    val straight = to - from
    val round = when {
        from < 0f || to < 0f -> straight
        straight > HALF_REP -> straight - 1f
        straight < -HALF_REP -> straight + 1f
        else -> straight
    }
    return if (round == straight) from + straight * share else looped(from + round * share)
}

/** A breath in and out at rest, and how long a drive's flare lasts. */
private const val BREATH_SECONDS = 3.6f
private const val PULSE_SECONDS = 0.45f
private const val TURN = 2 * PI
private const val HALF_REP = 0.5f
