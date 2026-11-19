package dev.justinsmith.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.heart_rate_chart_cd
import dev.justinsmith.core.resources.heart_rate_zone_short
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.HR_SAMPLE_SECONDS
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.platform.HeartRateZone
import dev.justinsmith.interval.platform.MAX_BPM_RANGE
import dev.justinsmith.interval.platform.zoneSeconds
import dev.justinsmith.interval.timer.formatClock
import org.jetbrains.compose.resources.stringResource

/**
 * Heart-rate line chart over a workout's [samples] (0 marks a gap where the sensor read nothing), drawn over
 * subtle horizontal training-zone bands keyed to [maxBpm].
 */
@Composable
fun HeartRateChart(
    samples: List<Int>,
    modifier: Modifier = Modifier,
    maxBpm: Int = DEFAULT_MAX_BPM,
    onWorkoutGround: Boolean = false,
) {
    val valid = samples.filter { it in HeartRate.BPM_RANGE }
    if (valid.size < 2) return
    val zones = if (onWorkoutGround) IntervalTheme.workout.heartZones else IntervalTheme.palette.heartZones
    val lineColor = if (onWorkoutGround) IntervalTheme.workout.onGround else IntervalTheme.palette.accent
    val description = stringResource(Res.string.heart_rate_chart_cd)
    val max = maxBpm.coerceIn(MAX_BPM_RANGE).toFloat()
    val minY = (valid.min().toFloat() - CHART_PADDING_BPM).coerceAtLeast(MIN_CHART_BPM)
    val maxY = (valid.max().toFloat() + CHART_PADDING_BPM).coerceAtLeast(minY + MIN_CHART_SPAN)

    Spacer(
        modifier
            .fillMaxWidth()
            .height(Sizes.heartChart)
            .semantics { contentDescription = description }
            .drawWithCache {
                val strokeWidth = Sizes.heartChartStroke.toPx()
                val stroke = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val step = if (samples.size > 1) size.width / (samples.size - 1) else 0f
                fun yFor(bpm: Float): Float {
                    val share = ((bpm - minY) / (maxY - minY)).coerceIn(0f, 1f)
                    return strokeWidth / 2f + (size.height - strokeWidth) * (1f - share)
                }
                val path = Path()
                var drawing = false
                samples.forEachIndexed { index, bpm ->
                    if (bpm !in HeartRate.BPM_RANGE) {
                        drawing = false
                    } else {
                        val y = yFor(bpm.toFloat())
                        if (drawing) path.lineTo(index * step, y) else path.moveTo(index * step, y)
                        drawing = true
                    }
                }
                val bands = listOf(
                    HeartRateZone.Zone1 to (0f to ZONE2_RATIO * max),
                    HeartRateZone.Zone2 to (ZONE2_RATIO * max to ZONE3_RATIO * max),
                    HeartRateZone.Zone3 to (ZONE3_RATIO * max to ZONE4_RATIO * max),
                    HeartRateZone.Zone4 to (ZONE4_RATIO * max to ZONE5_RATIO * max),
                    HeartRateZone.Zone5 to (ZONE5_RATIO * max to maxY),
                )
                onDrawBehind {
                    for ((zone, range) in bands) {
                        val lo = range.first.coerceIn(minY, maxY)
                        val hi = range.second.coerceIn(minY, maxY)
                        if (hi <= lo) continue
                        val top = yFor(hi)
                        val bottom = yFor(lo)
                        drawRect(
                            color = zones.of(zone).copy(alpha = ZONE_BAND_ALPHA),
                            topLeft = Offset(0f, top),
                            size = Size(size.width, bottom - top),
                        )
                    }
                    drawPath(path, lineColor, style = stroke)
                }
            },
    )
}

/**
 * Compact 5-zone time breakdown for [samples] (each covering [sampleSeconds] seconds), showing each zone's
 * colour, label, proportional bar, and duration. Zones with zero time are still shown dimmed so the ladder reads
 * consistently.
 */
@Composable
fun ZoneBreakdown(
    samples: List<Int>,
    modifier: Modifier = Modifier,
    maxBpm: Int = DEFAULT_MAX_BPM,
    sampleSeconds: Int = HR_SAMPLE_SECONDS,
    onWorkoutGround: Boolean = false,
) {
    val totals = zoneSeconds(samples, maxBpm, sampleSeconds)
    val totalSeconds = totals.values.sum()
    if (totalSeconds == 0) return
    val zones = if (onWorkoutGround) IntervalTheme.workout.heartZones else IntervalTheme.palette.heartZones
    val track = if (onWorkoutGround) IntervalTheme.workout.upcoming else IntervalTheme.palette.surfaceVariant
    val primaryText = if (onWorkoutGround) IntervalTheme.workout.onGround else IntervalTheme.palette.text
    val dimText = if (onWorkoutGround) IntervalTheme.workout.onGroundDim else IntervalTheme.palette.textDim

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        for (zone in HeartRateZone.entries.asReversed()) {
            val seconds = totals[zone] ?: 0
            val share = seconds.toFloat() / totalSeconds
            val color = zones.of(zone)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
            ) {
                Box(Modifier.size(Sizes.legendSwatch).clip(Corners.full).background(color))
                Text(
                    "${stringResource(Res.string.heart_rate_zone_short, zone.number)} ${heartRateZoneName(zone)}",
                    style = IntervalTheme.type.caption,
                    color = if (seconds > 0) primaryText else dimText,
                    modifier = Modifier.width(Sizes.zoneLabelWidth),
                    maxLines = 1,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(Sizes.zoneBarHeight)
                        .clip(Corners.full)
                        .background(track),
                ) {
                    if (share > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth(share.coerceIn(MIN_VISIBLE_SHARE, 1f))
                                .height(Sizes.zoneBarHeight)
                                .clip(Corners.full)
                                .background(color),
                        )
                    }
                }
                Text(
                    formatClock(seconds),
                    style = IntervalTheme.type.caption,
                    color = if (seconds > 0) primaryText else dimText,
                )
            }
        }
    }
}

private const val CHART_PADDING_BPM = 8f
private const val MIN_CHART_BPM = 50f
private const val MIN_CHART_SPAN = 20f
private const val ZONE_BAND_ALPHA = 0.12f
private const val ZONE2_RATIO = 0.6f
private const val ZONE3_RATIO = 0.7f
private const val ZONE4_RATIO = 0.8f
private const val ZONE5_RATIO = 0.9f
private const val MIN_VISIBLE_SHARE = 0.04f

@PreviewFormFactors
@Composable
private fun HeartRateChartPreview() {
    val samples = listOf(108, 118, 132, 146, 158, 168, 174, 162, 142, 155, 171, 178, 150, 128)
    IntervalTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
            HeartRateChart(samples)
            ZoneBreakdown(samples)
        }
    }
}
