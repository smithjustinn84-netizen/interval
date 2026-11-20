package dev.justinsmith.lift.ui.demo

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The breath the figure is at [progress] through the demo: from -1, breathed out, to 1, breathed in. It takes a
 * whole number of breaths to a rep, one about every [BREATH_SECONDS] and never fewer than one, so the breath
 * loops as the rep does; and it starts from rest, so the set-up's breath runs into the first rep's. Only the
 * chest shows it: a figure holding a plank still moves, and no joint is anywhere but where its pose puts it.
 */
internal fun ExerciseDemo.breathAt(progress: Float): Float {
    val breaths = if (progress < 0f) {
        progress * (setUp?.seconds ?: 0f) / BREATH_SECONDS
    } else {
        progress * (secondsPerRep / BREATH_SECONDS).roundToInt().coerceAtLeast(1)
    }
    return sin(breaths * TURN).toFloat()
}

/**
 * How fresh the drive is at [progress]: 1 at the moment a stretch of driving starts, easing to 0 over
 * [PULSE_SECONDS], and 0 through everything else, the set-up too. The weight flares with it, so each rep has an
 * accent where the effort goes in.
 */
internal fun ExerciseDemo.pulseAt(progress: Float): Float {
    if (progress < 0f) return 0f
    val index = phases.indexOfFirst { progress < it.until }.let { if (it < 0) phases.lastIndex else it }
    if (phases[index].beat != DemoBeat.Drive) return 0f
    val start = if (index == 0) 0f else phases[index - 1].until
    val left = 1f - (progress - start) * secondsPerRep / PULSE_SECONDS
    return if (left <= 0f) 0f else left * left
}

/**
 * The place [share] of the way from [from] to [to] in a demo, taken the short way: back or on through the rep,
 * or round its end into the next one when that is nearer, since the rep loops. Into or out of the set-up there is
 * one way only.
 */
internal fun placeBetween(from: Float, to: Float, share: Float): Float {
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
