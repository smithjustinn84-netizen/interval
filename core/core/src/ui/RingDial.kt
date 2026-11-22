package dev.justinsmith.interval.ui

import kotlin.math.ceil

/**
 * The time a ring's turn stands for, which makes it a dial: an interval of [seconds], the ring emptying as it
 * goes when [draining] (the share still to go, as the workout's) and filling otherwise (an EMOM's minute). From
 * it come the dial's ticks, a second or a rounder step each, and the heat of the interval's last seconds.
 */
data class RingDial(val seconds: Int, val draining: Boolean = false) {
    /** The seconds still to go with the ring at [progress]. */
    fun secondsLeft(progress: Float): Float = seconds * (if (draining) progress else 1f - progress).coerceIn(0f, 1f)

    /**
     * How far into its last seconds the interval is with the ring at [progress]: 0 until [urgentSeconds] are
     * left, easing up to 1 as it ends. What the ring's arc and the clock's digits brighten by.
     */
    fun heat(progress: Float): Float = heatAt(secondsLeft(progress), urgentSeconds)

    /**
     * How many of its last seconds are urgent: [Motion.URGENT_SECONDS], or of a shorter interval
     * [Motion.URGENT_SHARE], though never fewer than the countdown's [Motion.COUNTDOWN_SECONDS] (or all there are).
     */
    val urgentSeconds: Float
        get() = (seconds * Motion.URGENT_SHARE)
            .coerceIn(minOf(seconds, Motion.COUNTDOWN_SECONDS).toFloat(), Motion.URGENT_SECONDS.toFloat())
}

/** 0 with more than [urgent] seconds [left], easing in and out up to 1 with none. */
internal fun heatAt(left: Float, urgent: Float): Float {
    if (urgent <= 0f) return 0f
    val into = (1f - left / urgent).coerceIn(0f, 1f)
    return into * into * (EASE_EDGE - EASE_SLOPE * into)
}

/**
 * The beat of an interval's last seconds, worked out from the seconds [left] alone, so a ring that glides beats
 * with no clock of its own and stops where a pause leaves it: 1 as the clock shows a new second, gone
 * [Motion.CLOCK_POP_MILLIS] later. It lands on every other second of the [urgent] ones and on each of the
 * countdown's [Motion.COUNTDOWN_SECONDS], so the cadence doubles as the beeps start.
 */
internal fun beatAt(left: Float, urgent: Float): Float {
    if (left <= 0f || left > urgent) return 0f
    val every = if (left <= Motion.COUNTDOWN_SECONDS) 1f else Motion.URGENT_BEAT_SECONDS
    val since = ceil(left / every) * every - left
    return (1f - since * MILLIS_PER_SECOND / Motion.CLOCK_POP_MILLIS).coerceIn(0f, 1f)
}

/**
 * A dial's ticks: [count] to the turn, every [majorEvery]th a long one. The count is a fraction when the step
 * doesn't divide the interval; the ticks then run from twelve o'clock and the last gap is the short one.
 */
internal data class DialTicks(val count: Float, val majorEvery: Int)

/**
 * The ticks for an interval of [seconds], or null for none: a tick a second while that is no more than
 * [MAX_TICKS], else the first rounder step that is, with a long tick at each step up (five seconds among
 * single ones, a minute among quarters of it).
 */
internal fun dialTicks(seconds: Int): DialTicks? {
    if (seconds <= 0) return null
    val step = tickSteps.firstOrNull { seconds <= it.seconds * MAX_TICKS } ?: tickSteps.last()
    return DialTicks(seconds.toFloat() / step.seconds, step.majorEvery)
}

/** A tick every [seconds], and every [majorEvery]th of them long. */
private class TickStep(val seconds: Int, val majorEvery: Int)

@Suppress("detekt.MagicNumber") // The table of steps itself: seconds, then how many make the next step up.
private val tickSteps = listOf(
    TickStep(seconds = 1, majorEvery = 5),
    TickStep(seconds = 5, majorEvery = 6),
    TickStep(seconds = 15, majorEvery = 4),
    TickStep(seconds = 60, majorEvery = 5),
    TickStep(seconds = 300, majorEvery = 6),
    TickStep(seconds = 900, majorEvery = 4),
)

/** The most ticks a dial carries: a second each round a minute, as a watch's. */
private const val MAX_TICKS = 60

/** Smoothstep's two numbers: t² (3 − 2t). */
private const val EASE_EDGE = 3f
private const val EASE_SLOPE = 2f

private const val MILLIS_PER_SECOND = 1000f
