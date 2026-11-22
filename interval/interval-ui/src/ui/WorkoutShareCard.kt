package dev.justinsmith.interval.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.timer.IntervalConfig

/**
 * A finished workout as a picture: core's [ShareCard] with the workout's [config] as its timeline, the
 * waveform of its works and rests, and under it the heart rate a sensor read through it ([heartRateSamples]),
 * when one did. The summary and its page in the Log both share it, each with its own words.
 */
@Composable
fun WorkoutShareCard(
    eyebrow: String,
    title: String,
    config: IntervalConfig,
    stats: List<ShareStat>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    heartRateSamples: List<Int>? = null,
    maxHeartRate: Int = DEFAULT_MAX_BPM,
) {
    ShareCard(eyebrow, title, stats, modifier, subtitle) {
        val samples = heartRateSamples?.takeIf { it.isNotEmpty() }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.space20)) {
            Timeline(
                config,
                Modifier.fillMaxWidth(),
                height = if (samples == null) IntervalSizes.shareTimelineAlone else IntervalSizes.shareTimeline,
            )
            samples?.let { HeartRateChart(it, Modifier.fillMaxWidth(), maxBpm = maxHeartRate) }
        }
    }
}

@PreviewFormFactors
@Composable
private fun WorkoutShareCardPreview() = FittedShareCard {
    WorkoutShareCard(
        eyebrow = "Sunday, Nov 22 · 7:05 AM",
        title = "Tabata",
        subtitle = "20s on · 10s off · ×8",
        config = IntervalConfig(workSeconds = 20, restSeconds = 10, rounds = 8),
        stats = listOf(ShareStat("4:00", "Time"), ShareStat("8/8", "Rounds"), ShareStat("62", "kcal")),
        heartRateSamples = previewHeartRate,
    )
}

@PreviewFormFactors
@Composable
private fun WorkoutShareCardNoHeartPreview() = FittedShareCard {
    WorkoutShareCard(
        eyebrow = "Workout done",
        title = "15:15 MVO2",
        subtitle = "15s on · 15s off · ×20",
        config = IntervalConfig(workSeconds = 15, restSeconds = 15, rounds = 20),
        stats = listOf(ShareStat("10:00", "Time"), ShareStat("20/20", "Rounds")),
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
private const val PREVIEW_RISE = 40
private const val PREVIEW_PERIOD = 4
private const val PREVIEW_FLOOR = 110
private const val PREVIEW_SWING = 6
