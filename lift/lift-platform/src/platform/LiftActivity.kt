package dev.justinsmith.lift.platform

import dev.justinsmith.interval.platform.LiveActivityContent
import dev.justinsmith.interval.platform.LiveActivitySegment
import dev.justinsmith.interval.platform.LiveActivitySpan
import dev.justinsmith.lift.domain.EmomClock
import dev.justinsmith.lift.domain.HoldClock

/**
 * The Live Activity of [run]: one span, its start to its end, which the system counts down by itself, so it stays
 * right with the app suspended. Over, it says [overTitle] ("Rest over") until the app is back and ends it. The bar is
 * the run's count-in, then an EMOM's minutes one by one, a density block's cap or a hold's seconds; a rest is one.
 */
fun liftActivity(run: TimerRun, title: String, overTitle: String): LiveActivityContent = LiveActivityContent(
    title = title,
    staleTitle = overTitle,
    interval = LiveActivitySpan(run.startedAtEpochMillis, run.endAtEpochMillis),
    segments = run.segmentSeconds().map { LiveActivitySegment(it) },
)

private fun TimerRun.segmentSeconds(): List<Int> = when (this) {
    is TimerRun.Rest -> listOf(seconds(endAtEpochMillis - startedAtEpochMillis))
    is TimerRun.Emom -> listOf(seconds(EmomClock.COUNT_IN_MILLIS)) + List(minutes) { seconds(EmomClock.MINUTE_MILLIS) }
    is TimerRun.Density -> listOf(seconds(EmomClock.COUNT_IN_MILLIS), seconds(minutes * EmomClock.MINUTE_MILLIS))
    is TimerRun.Hold -> listOf(seconds(HoldClock.COUNT_IN_MILLIS), seconds)
}.filter { it > 0 }

private fun seconds(millis: Long): Int = (millis / EmomClock.SECOND_MILLIS).toInt()
