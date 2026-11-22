package dev.justinsmith.interval.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.WeekStanding

/**
 * Picks the primary kinematic movement silhouette for an interval workout's [ShareCard]: a mobility flow's
 * leading Lift figure ([flowRep]), a snatch for alternating-hand kettlebell intervals ([asksSnatches]), a
 * kettlebell swing for other kettlebell intervals, or null when no specific movement applies.
 */
fun workoutShareMovement(
    config: IntervalConfig,
    asksSnatches: Boolean = false,
    flowRep: ExerciseRep? = null,
): ExerciseRep? = when {
    flowRep != null -> flowRep
    asksSnatches -> KettlebellReps.Snatch
    config.activity == Activity.Kettlebell -> KettlebellReps.Swing
    else -> null
}

/**
 * A finished workout as a printed telemetry card: core's [ShareCard] compositing the workout's stepped [Timeline]
 * waveform, [HeartRateChart] with zone-banding shader ([heartRateSamples]), [WeekStanding] dial badge, optional
 * `DemoPainter` [movement] watermark silhouette, and tabular Barlow Condensed [stats].
 */
@Composable
fun WorkoutShareCard(
    eyebrow: String,
    title: String,
    config: IntervalConfig,
    stats: List<ShareStat>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    week: WeekStanding? = null,
    heartRateSamples: List<Int>? = null,
    heartRateMarks: List<ClosedFloatingPointRange<Float>> = emptyList(),
    maxHeartRate: Int = DEFAULT_MAX_BPM,
    movement: ExerciseRep? = null,
    cardConfig: ShareCardConfig = ShareCardConfig(),
) {
    val heartRate = heartRateSamples?.let { ShareHeartRate(it, maxHeartRate, heartRateMarks) }?.takeIf { it.hasChart }
    val showHeart = heartRate != null && cardConfig.showHeartRate
    ShareCard(
        eyebrow = eyebrow,
        title = title,
        stats = stats,
        modifier = modifier,
        subtitle = subtitle,
        week = week,
        heartRate = heartRate,
        movement = movement,
        config = cardConfig,
    ) {
        Timeline(
            config,
            Modifier.fillMaxWidth(),
            height = if (showHeart) IntervalSizes.shareTimeline else IntervalSizes.shareTimelineAlone,
        )
    }
}

@PreviewFormFactors
@Composable
private fun WorkoutShareCardPreview() = FittedShareCard {
    WorkoutShareCard(
        eyebrow = "Sunday, Nov 22 · 7:05 AM",
        title = "15:15 MVO2",
        subtitle = "15s on · 15s off · ×20",
        config = IntervalConfig(workSeconds = 15, restSeconds = 15, rounds = 20, activity = Activity.Kettlebell),
        stats = listOf(
            ShareStat("10:00", "Time"),
            ShareStat("5:00", "Work"),
            ShareStat("178", "Peak BPM", unit = "bpm", accent = true),
            ShareStat("142", "Calories", unit = "kcal"),
        ),
        week = WeekStanding(count = 4, goal = 5, streak = 3),
        heartRateSamples = previewHeartRate,
        heartRateMarks = previewHeartMarks,
        movement = KettlebellReps.Snatch,
    )
}

@PreviewFormFactors
@Composable
private fun WorkoutShareCardNoHeartPreview() = FittedShareCard {
    WorkoutShareCard(
        eyebrow = "Workout done",
        title = "Tabata",
        subtitle = "20s on · 10s off · ×8",
        config = IntervalConfig(workSeconds = 20, restSeconds = 10, rounds = 8),
        stats = listOf(
            ShareStat("4:00", "Time"),
            ShareStat("2:40", "Work"),
            ShareStat("8/8", "Rounds"),
        ),
        week = WeekStanding(count = 3, goal = 4, streak = 1),
    )
}

/** Twelve minutes climbing in and out of the upper zones, five seconds a sample. */
private val previewHeartRate: List<Int> = List(PREVIEW_SAMPLES) { index ->
    val rising = minOf(index, PREVIEW_RAMP) * PREVIEW_RISE / PREVIEW_RAMP
    val wave = if (index / PREVIEW_PERIOD % 2 == 0) index % PREVIEW_PERIOD else PREVIEW_PERIOD - index % PREVIEW_PERIOD
    PREVIEW_FLOOR + rising + wave * PREVIEW_SWING
}

private const val PREVIEW_SAMPLES = 48
private const val PREVIEW_RAMP = 12
private const val PREVIEW_RISE = 44
private const val PREVIEW_PERIOD = 4
private const val PREVIEW_FLOOR = 110
private const val PREVIEW_SWING = 6
private val previewHeartMarks = listOf(0.14f..0.34f, 0.48f..0.68f, 0.78f..0.94f)
