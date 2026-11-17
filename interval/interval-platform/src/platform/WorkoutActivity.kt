package dev.justinsmith.interval.platform

import dev.justinsmith.interval.timer.MILLIS_PER_SECOND
import dev.justinsmith.interval.timer.OutlinedInterval
import dev.justinsmith.interval.timer.UpcomingChange
import dev.justinsmith.interval.timer.WorkoutOutline

/** The words a workout's Live Activity shows, read from the platform's strings. */
data class WorkoutActivityWords(
    /** The phase, or a flow's move ("Work", "Pigeon · Left side"). */
    val title: String,
    /** The round, set and block, or the move among the moves; null for none (the warm-up). */
    val detail: String?,
    /** Once the interval is over and the app is away. */
    val staleTitle: String,
    /** What the whole workout's clock is of. */
    val runLabel: String,
    /** Its buttons: Pause or Resume, as it stands, and End. */
    val toggleLabel: String,
    val endLabel: String,
)

/**
 * The Live Activity of the workout at [status], [nowMillis] on the wall clock, with its [changes] ahead
 * ([ActiveWorkout.upcomingChanges], nearest first, down to the end). The interval counts down to the first change
 * and the whole workout to the last; the workout began its [WorkoutOutline.totalSeconds] before that, so the bar
 * crosses it at the workout's pace. Paused, or with nothing ahead (done), its clocks stand still: the interval's at
 * [WorkoutStatus.clock], the bar at how far through it is.
 */
fun workoutActivity(
    status: WorkoutStatus,
    changes: List<UpcomingChange>,
    nowMillis: Long,
    colors: StatusColors,
    words: WorkoutActivityWords,
): LiveActivityContent {
    val outline = status.outline
    val total = outline.totalSeconds
    val still = LiveActivityContent(
        title = words.title,
        detail = words.detail,
        staleTitle = words.staleTitle,
        colorArgb = if (status.paused) colors.paused else colors.of(status.phase),
        paused = status.paused,
        pausedClock = status.clock,
        runLabel = words.runLabel,
        progress = if (total > 0) outline.elapsedSeconds.toDouble() / total else 0.0,
        segments = activitySegments(outline, colors),
        toggleLabel = words.toggleLabel,
        endLabel = words.endLabel,
    )
    // Over, there is nothing to pause or end: the summary waits in the app.
    if (!status.paused && changes.isEmpty()) return still.copy(toggleLabel = null, endLabel = null)
    if (status.paused) return still
    val intervalEnd = nowMillis + changes.first().offsetMillis
    val runEnd = nowMillis + changes.last().offsetMillis
    val current = outline.intervals.firstOrNull {
        it.phase == status.phase && it.round == status.round && it.set == status.set && it.block == status.block
    }
    val intervalStart = current?.let { intervalEnd - it.seconds * MILLIS_PER_SECOND } ?: nowMillis
    return still.copy(
        pausedClock = null,
        interval = LiveActivitySpan(intervalStart.coerceAtMost(nowMillis), intervalEnd),
        run = LiveActivitySpan((runEnd - total * MILLIS_PER_SECOND).coerceAtMost(nowMillis), runEnd),
    )
}

/**
 * The bar: each interval in its phase's colour, up to [MAX_ACTIVITY_SEGMENTS] of them; a longer workout as
 * Android's notification draws it ([StatusBar.of]: a segment a set, or one in the title's colour).
 */
internal fun activitySegments(outline: WorkoutOutline, colors: StatusColors): List<LiveActivitySegment> {
    if (outline.intervals.size <= MAX_ACTIVITY_SEGMENTS) {
        return outline.intervals.filter { it.seconds > 0 }.map { it.segment(colors) }
    }
    return StatusBar.of(outline)?.segments.orEmpty().map { segment ->
        LiveActivitySegment(segment.length, segment.phase?.let(colors::of))
    }
}

private fun OutlinedInterval.segment(colors: StatusColors) = LiveActivitySegment(seconds, colors.of(phase))

/**
 * As many segments as a Lock Screen's width still draws apart, and few enough to stay well inside the 4 KB an
 * activity's state may take.
 */
internal const val MAX_ACTIVITY_SEGMENTS = 48
