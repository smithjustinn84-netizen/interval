package dev.justinsmith.interval.workout

import dev.justinsmith.interval.model.CourseSpan
import dev.justinsmith.interval.platform.HealthMove
import dev.justinsmith.interval.platform.HealthSegment
import dev.justinsmith.interval.platform.HealthWorkout
import dev.justinsmith.interval.platform.HealthWorkoutKind
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.Phase

/**
 * A finished interval workout as the health store takes it: one high-intensity session (a stretching one, of
 * [Activity.Mobility]) from
 * [startedAtEpochMillis] to [endedAtEpochMillis], named for its preset when it had one, with a segment
 * per work interval in the words of [activity], per rest, and per pause. Get ready is time in the
 * session with no segment. Spans outside the session, or of no length, are left out, and one that
 * starts before the last ended is cut to start there, as the store takes no overlap.
 */
internal fun healthWorkout(
    activity: Activity,
    presetName: String?,
    startedAtEpochMillis: Long,
    endedAtEpochMillis: Long,
    spans: List<CourseSpan>,
    calories: Int?,
): HealthWorkout {
    val segments = mutableListOf<HealthSegment>()
    var last = startedAtEpochMillis
    spans.forEach { span ->
        val move = span.phase.healthMove(activity)
        val start = maxOf(span.startEpochMillis, last)
        val end = minOf(span.endEpochMillis, endedAtEpochMillis)
        if (move != null && end > start) {
            segments += HealthSegment(start, end, move)
            last = end
        }
    }
    return HealthWorkout(
        id = "interval-$startedAtEpochMillis",
        kind = if (activity == Activity.Mobility) HealthWorkoutKind.Mobility else HealthWorkoutKind.Intervals,
        title = presetName,
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = endedAtEpochMillis,
        segments = segments,
        calories = calories,
    )
}

/**
 * The move a span of [this] phase is, spent on [activity]; null for get ready, which is no segment. Health
 * Connect has no warm-up or cool-down segment, and both are the activity done easier, so they are its move.
 */
private fun Phase?.healthMove(activity: Activity): HealthMove? = when (this) {
    null -> HealthMove.Pause
    Phase.Work, Phase.WarmUp, Phase.CoolDown -> activity.healthMove
    Phase.Rest, Phase.SetRest -> HealthMove.Rest
    Phase.Prep, Phase.Done -> null
}

/** The store's word for work on [this]: the machine or the lift where it has one, else interval work. */
internal val Activity.healthMove: HealthMove
    get() = when (this) {
        Activity.Bike -> HealthMove.Biking
        Activity.Rower -> HealthMove.Rowing
        Activity.Running -> HealthMove.Running
        Activity.JumpRope -> HealthMove.JumpRope
        Activity.Kettlebell -> HealthMove.KettlebellSwing
        Activity.Barbell -> HealthMove.Weightlifting
        Activity.Mobility -> HealthMove.Stretching
        Activity.General, Activity.SkiErg, Activity.Bodyweight, Activity.Climbing -> HealthMove.Work
    }
