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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.heart_rate_chart_cd
import dev.justinsmith.core.resources.heart_rate_zone_short
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.HR_SAMPLE_SECONDS
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.platform.HeartRateZone
import dev.justinsmith.interval.platform.startShare
import dev.justinsmith.interval.platform.zoneCutoff
import dev.justinsmith.interval.platform.zoneSeconds
import dev.justinsmith.interval.timer.formatClock
import org.jetbrains.compose.resources.stringResource

/**
 * Heart-rate line chart over a workout's [samples] (0 marks a gap where the sensor read nothing), drawn over
 * subtle horizontal training-zone bands keyed to [maxBpm]. Each of [marks] is a stretch worth telling apart (a
 * work interval, a class's round), its start and end as shares of the chart's width, shaded behind the line.
 * [height] is the chart's, shorter where it is drawn live under a clock.
 */
@Composable
fun HeartRateChart(
    samples: List<Int>,
    modifier: Modifier = Modifier,
    maxBpm: Int = DEFAULT_MAX_BPM,
    onWorkoutGround: Boolean = false,
    marks: List<ClosedFloatingPointRange<Float>> = emptyList(),
    height: Dp = Sizes.heartChart,
) {
    val valid = samples.filter { it in HeartRate.BPM_RANGE }
    if (valid.size < 2) return
    val zones = if (onWorkoutGround) IntervalTheme.workout.heartZones else IntervalTheme.palette.heartZones
    val lineColor = if (onWorkoutGround) IntervalTheme.workout.onGround else IntervalTheme.palette.accent
    val markColor = (if (onWorkoutGround) IntervalTheme.workout.onGround else IntervalTheme.palette.text)
        .copy(alpha = MARK_ALPHA)
    val description = stringResource(Res.string.heart_rate_chart_cd)
    val resting = LocalRestingHeartRate.current
    val minY = (valid.min().toFloat() - CHART_PADDING_BPM).coerceAtLeast(MIN_CHART_BPM)
    val maxY = (valid.max().toFloat() + CHART_PADDING_BPM).coerceAtLeast(minY + MIN_CHART_SPAN)

    Spacer(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .drawWithCache {
                val strokeWidth = Sizes.heartChartStroke.toPx()
                val stroke = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val step = if (samples.size > 1) size.width / (samples.size - 1) else 0f
                fun yFor(bpm: Float): Float {
                    val share = ((bpm - minY) / (maxY - minY)).coerceIn(0f, 1f)
                    return strokeWidth / 2f + (size.height - strokeWidth) * (1f - share)
                }
                val path = heartPath(samples, step, ::yFor)
                // Each zone from where it starts to where the next does, the last to the chart's top.
                val bands = HeartRateZone.entries.map { zone ->
                    val above = HeartRateZone.entries.getOrNull(zone.ordinal + 1)
                    val from = if (zone == HeartRateZone.Zone1) 0f else zoneCutoff(zone.startShare, maxBpm, resting)
                    zone to (from to (above?.let { zoneCutoff(it.startShare, maxBpm, resting) } ?: maxY))
                }
                onDrawBehind {
                    for ((zone, range) in bands) {
                        val lo = range.first.coerceIn(minY, maxY)
                        val hi = range.second.coerceIn(minY, maxY)
                        if (hi > lo) drawBand(zones.of(zone), top = yFor(hi), bottom = yFor(lo))
                    }
                    marks.forEach { drawMark(it, markColor) }
                    drawPath(path, lineColor, style = stroke)
                }
            },
    )
}

/** The line through [samples], [step] apart and each at the height [yFor] gives it, broken where none was read. */
private fun heartPath(samples: List<Int>, step: Float, yFor: (Float) -> Float): Path {
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
    return path
}

/** A zone's band across the chart, from [top] down to [bottom]. */
private fun DrawScope.drawBand(color: Color, top: Float, bottom: Float) {
    drawRect(color.copy(alpha = ZONE_BAND_ALPHA), topLeft = Offset(0f, top), size = Size(size.width, bottom - top))
}

/** A stretch of the chart shaded its whole height: [mark]'s start and end as shares of the width. */
private fun DrawScope.drawMark(mark: ClosedFloatingPointRange<Float>, color: Color) {
    val left = mark.start.coerceIn(0f, 1f) * size.width
    val right = mark.endInclusive.coerceIn(0f, 1f) * size.width
    if (right > left) drawRect(color, Offset(left, 0f), Size(right - left, size.height))
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
    val totals = zoneSeconds(samples, maxBpm, sampleSeconds, LocalRestingHeartRate.current)
    val totalSeconds = totals.values.sum()
    if (totalSeconds == 0) return
    val ink = zoneInk(onWorkoutGround)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        for (zone in HeartRateZone.entries.asReversed()) {
            val seconds = totals[zone] ?: 0
            ZoneRow(zone, seconds, share = seconds.toFloat() / totalSeconds, ink = ink)
        }
    }
}

/** What a zone's row is drawn in: on the workout's dark ground, or the app's. */
private class ZoneInk(val zones: HeartZoneColors, val track: Color, val text: Color, val dim: Color)

@Composable
private fun zoneInk(onWorkoutGround: Boolean): ZoneInk = if (onWorkoutGround) {
    val workout = IntervalTheme.workout
    ZoneInk(workout.heartZones, workout.upcoming, workout.onGround, workout.onGroundDim)
} else {
    val palette = IntervalTheme.palette
    ZoneInk(palette.heartZones, palette.surfaceVariant, palette.text, palette.textDim)
}

/** A zone's swatch and name, its share of the time as a bar, and the time. */
@Composable
private fun ZoneRow(zone: HeartRateZone, seconds: Int, share: Float, ink: ZoneInk, modifier: Modifier = Modifier) {
    val color = ink.zones.of(zone)
    val textColor = if (seconds > 0) ink.text else ink.dim
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Box(Modifier.size(Sizes.legendSwatch).clip(Corners.full).background(color))
        Text(
            "${stringResource(Res.string.heart_rate_zone_short, zone.number)} ${heartRateZoneName(zone)}",
            style = IntervalTheme.type.caption,
            color = textColor,
            modifier = Modifier.width(Sizes.zoneLabelWidth),
            maxLines = 1,
        )
        Box(
            Modifier
                .weight(1f)
                .height(Sizes.zoneBarHeight)
                .clip(Corners.full)
                .background(ink.track),
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
        Text(formatClock(seconds), style = IntervalTheme.type.caption, color = textColor)
    }
}

private const val CHART_PADDING_BPM = 8f
private const val MIN_CHART_BPM = 50f
private const val MIN_CHART_SPAN = 20f
private const val ZONE_BAND_ALPHA = 0.12f
private const val MARK_ALPHA = 0.1f
private const val MIN_VISIBLE_SHARE = 0.04f

@PreviewFormFactors
@Composable
private fun HeartRateChartPreview() {
    val samples = listOf(108, 118, 132, 146, 158, 168, 174, 162, 142, 155, 171, 178, 150, 128)
    val marks = listOf(0.2f..0.5f, 0.65f..0.9f)
    IntervalTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
            HeartRateChart(samples, marks = marks)
            ZoneBreakdown(samples)
        }
    }
}
