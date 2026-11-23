package dev.justinsmith.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.heart_rate_chart_cd
import dev.justinsmith.core.resources.heart_rate_zone_short
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.HR_SAMPLE_SECONDS
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.platform.HeartRateZone
import dev.justinsmith.interval.platform.heartRateZone
import dev.justinsmith.interval.platform.zoneSeconds
import dev.justinsmith.interval.timer.formatClock
import org.jetbrains.compose.resources.stringResource

/**
 * Heart-rate line chart over a workout's [samples] (0 marks a gap where the sensor read nothing), with a
 * zone-banded area fill shader beneath the curve keyed to [maxBpm] and the horizontal thresholds from
 * [HeartZoneScale]. Each of [marks] is a stretch worth telling apart (a work interval, a class's round),
 * its start and end as shares of the chart's width, shaded behind the curve. [height] is the chart's, shorter
 * where it is drawn live under a clock. [surfaceColor] is the host surface from [IntervalTheme] that the fill
 * attenuates into and the top stroke contrasts against; [liveReading] highlights the trailing live sample.
 */
@Composable
fun HeartRateChart(
    samples: List<Int>,
    modifier: Modifier = Modifier,
    maxBpm: Int = DEFAULT_MAX_BPM,
    onWorkoutGround: Boolean = false,
    marks: List<ClosedFloatingPointRange<Float>> = emptyList(),
    height: Dp = Sizes.heartChart,
    surfaceColor: Color = if (onWorkoutGround) IntervalTheme.workout.control else IntervalTheme.palette.surface,
    liveReading: Boolean = false,
) {
    // Its range, worked out as the samples change rather than at each composition: a live trace's parent
    // recomposes every second, the samples only every five.
    val range = remember(samples) { ChartRange.of(samples) } ?: return
    val zones = if (onWorkoutGround) IntervalTheme.workout.heartZones else IntervalTheme.palette.heartZones
    val lineColor = if (onWorkoutGround) IntervalTheme.workout.onGround else IntervalTheme.palette.text
    val markColor = (if (onWorkoutGround) IntervalTheme.workout.onGround else IntervalTheme.palette.text)
        .copy(alpha = MARK_ALPHA)
    val description = stringResource(Res.string.heart_rate_chart_cd)
    val resting = LocalRestingHeartRate.current
    val minY = range.minY
    val maxY = range.maxY
    val thresholds = remember(maxBpm, resting) { heartZoneThresholds(maxBpm, resting) }
    val program = remember { shaderProgram(HEART_RATE_CHART_SHADER) }

    Spacer(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .drawWithCache {
                val scene = buildHeartChartScene(
                    samples = samples,
                    marks = marks,
                    maxBpm = maxBpm,
                    resting = resting,
                    minY = minY,
                    maxY = maxY,
                    thresholds = thresholds,
                    zones = zones,
                    surfaceColor = surfaceColor,
                    lineColor = lineColor,
                    markColor = markColor,
                    liveReading = liveReading,
                    program = program,
                )
                onDrawBehind { drawHeartChartScene(scene) }
            },
    )
}

/** The beats a chart is drawn between: its readings' lowest and highest, padded; null under two readings. */
private class ChartRange(val minY: Float, val maxY: Float) {
    companion object {
        fun of(samples: List<Int>): ChartRange? {
            val valid = samples.filter { it in HeartRate.BPM_RANGE }
            if (valid.size < 2) return null
            val minY = (valid.min().toFloat() - CHART_PADDING_BPM).coerceAtLeast(MIN_CHART_BPM)
            return ChartRange(minY, (valid.max().toFloat() + CHART_PADDING_BPM).coerceAtLeast(minY + MIN_CHART_SPAN))
        }
    }
}

@Suppress("detekt.LongParameterList")
private fun CacheDrawScope.buildHeartChartScene(
    samples: List<Int>,
    marks: List<ClosedFloatingPointRange<Float>>,
    maxBpm: Int,
    resting: Int?,
    minY: Float,
    maxY: Float,
    thresholds: HeartZoneThresholds,
    zones: HeartZoneColors,
    surfaceColor: Color,
    lineColor: Color,
    markColor: Color,
    liveReading: Boolean,
    program: ShaderProgram?,
): HeartChartScene {
    val strokeWidth = Sizes.heartChartStroke.toPx()
    val casingExtra = STROKE_CASING_EXTRA_DP.dp.toPx()
    val liveGlowRadius = LIVE_DOT_GLOW_RADIUS_DP.dp.toPx()
    val leftInset = strokeWidth / 2f
    val rightInset = if (liveReading) liveGlowRadius else strokeWidth / 2f
    val plotWidth = (size.width - leftInset - rightInset).coerceAtLeast(0f)
    val step = if (samples.size > 1) plotWidth / (samples.size - 1) else 0f
    val yFor: (Float) -> Float = { bpm ->
        clampedHeartChartY(bpm, minY, maxY, size.height, strokeWidth)
    }
    val thresholdsY = heartChartThresholdsY(thresholds, minY, maxY, size.height, strokeWidth)
    val runs = validSampleRuns(samples)
    val fillBrush = if (program != null) {
        bindHeartChartShader(program, size, density, thresholdsY, zones, surfaceColor)
    } else {
        heartChartFallbackBrush(size.height, thresholdsY, zones, surfaceColor)
    }
    return HeartChartScene(
        bands = cachedZoneBands(zones, thresholds, minY, maxY, size, yFor),
        thresholdLines = cachedThresholdLines(zones, thresholdsY, size),
        thresholdStrokeWidth = THRESHOLD_LINE_DP.dp.toPx(),
        marks = cachedChartMarks(marks, size),
        markColor = markColor,
        linePath = buildHeartLinePath(samples, runs, leftInset, step, yFor),
        fillPath = buildHeartFillPath(samples, runs, leftInset, step, size.height, yFor),
        fillBrush = fillBrush,
        lineColor = lineColor,
        stroke = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        casingColor = surfaceColor.copy(alpha = STROKE_CASING_ALPHA),
        casingStroke = Stroke(strokeWidth + casingExtra, cap = StrokeCap.Round, join = StrokeJoin.Round),
        endpoint = if (liveReading) {
            buildLiveEndpoint(
                samples = samples,
                maxBpm = maxBpm,
                resting = resting,
                zones = zones,
                surfaceColor = surfaceColor,
                leftInset = leftInset,
                step = step,
                yFor = yFor,
                glowRadius = liveGlowRadius,
                ringRadius = LIVE_DOT_RING_RADIUS_DP.dp.toPx(),
                coreRadius = LIVE_DOT_CORE_RADIUS_DP.dp.toPx(),
            )
        } else {
            null
        },
    )
}

/** Paints [scene] with pre-cached geometry, brushes, and strokes so draw frames allocate nothing. */
private fun DrawScope.drawHeartChartScene(scene: HeartChartScene) {
    for (i in 0..<scene.bands.size) {
        val band = scene.bands[i]
        drawRect(band.color, topLeft = band.topLeft, size = band.size)
    }
    for (i in 0..<scene.thresholdLines.size) {
        val line = scene.thresholdLines[i]
        drawLine(line.color, start = line.start, end = line.end, strokeWidth = scene.thresholdStrokeWidth)
    }
    for (i in 0..<scene.marks.size) {
        val mark = scene.marks[i]
        drawRect(scene.markColor, topLeft = mark.topLeft, size = mark.size)
    }
    drawPath(scene.fillPath, scene.fillBrush)
    drawPath(scene.linePath, scene.casingColor, style = scene.casingStroke)
    drawPath(scene.linePath, scene.lineColor, style = scene.stroke)
    val endpoint = scene.endpoint
    if (endpoint != null) {
        drawCircle(endpoint.glowColor, radius = endpoint.glowRadius, center = endpoint.center)
        drawCircle(endpoint.ringColor, radius = endpoint.ringRadius, center = endpoint.center)
        drawCircle(endpoint.dotColor, radius = endpoint.coreRadius, center = endpoint.center)
    }
}

private class HeartChartScene(
    val bands: List<CachedBand>,
    val thresholdLines: List<CachedThresholdLine>,
    val thresholdStrokeWidth: Float,
    val marks: List<CachedMark>,
    val markColor: Color,
    val linePath: Path,
    val fillPath: Path,
    val fillBrush: Brush,
    val lineColor: Color,
    val stroke: Stroke,
    val casingColor: Color,
    val casingStroke: Stroke,
    val endpoint: LiveEndpoint?,
)

private class CachedBand(val color: Color, val topLeft: Offset, val size: Size)

private class CachedThresholdLine(val color: Color, val start: Offset, val end: Offset)

private class CachedMark(val topLeft: Offset, val size: Size)

private class LiveEndpoint(
    val center: Offset,
    val dotColor: Color,
    val glowColor: Color,
    val ringColor: Color,
    val glowRadius: Float,
    val ringRadius: Float,
    val coreRadius: Float,
)

private fun cachedZoneBands(
    zones: HeartZoneColors,
    thresholds: HeartZoneThresholds,
    minY: Float,
    maxY: Float,
    size: Size,
    yFor: (Float) -> Float,
): List<CachedBand> {
    val result = ArrayList<CachedBand>(HeartRateZone.entries.size)
    for (zone in HeartRateZone.entries) {
        val from = when (zone) {
            HeartRateZone.Zone1 -> 0f
            HeartRateZone.Zone2 -> thresholds.zone2
            HeartRateZone.Zone3 -> thresholds.zone3
            HeartRateZone.Zone4 -> thresholds.zone4
            HeartRateZone.Zone5 -> thresholds.zone5
        }
        val to = when (zone) {
            HeartRateZone.Zone1 -> thresholds.zone2
            HeartRateZone.Zone2 -> thresholds.zone3
            HeartRateZone.Zone3 -> thresholds.zone4
            HeartRateZone.Zone4 -> thresholds.zone5
            HeartRateZone.Zone5 -> maxY
        }
        val lo = from.coerceIn(minY, maxY)
        val hi = to.coerceIn(minY, maxY)
        if (hi > lo) {
            val top = yFor(hi)
            val bottom = yFor(lo)
            result.add(
                CachedBand(
                    color = zones.of(zone).copy(alpha = ZONE_BAND_ALPHA),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, bottom - top),
                ),
            )
        }
    }
    return result
}

private fun cachedThresholdLines(
    zones: HeartZoneColors,
    thresholdsY: HeartChartThresholdsY,
    size: Size,
): List<CachedThresholdLine> {
    val result = ArrayList<CachedThresholdLine>(THRESHOLD_COUNT)
    fun addIfVisible(y: Float, color: Color) {
        if (y > 0f && y < size.height) {
            result.add(
                CachedThresholdLine(
                    color = color.copy(alpha = THRESHOLD_LINE_ALPHA),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                ),
            )
        }
    }
    addIfVisible(thresholdsY.zone2, zones.zone2)
    addIfVisible(thresholdsY.zone3, zones.zone3)
    addIfVisible(thresholdsY.zone4, zones.zone4)
    addIfVisible(thresholdsY.zone5, zones.zone5)
    return result
}

private fun cachedChartMarks(marks: List<ClosedFloatingPointRange<Float>>, size: Size): List<CachedMark> {
    if (marks.isEmpty()) return emptyList()
    val result = ArrayList<CachedMark>(marks.size)
    for (i in 0..<marks.size) {
        val mark = marks[i]
        val left = mark.start.coerceIn(0f, 1f) * size.width
        val right = mark.endInclusive.coerceIn(0f, 1f) * size.width
        if (right > left) {
            result.add(CachedMark(Offset(left, 0f), Size(right - left, size.height)))
        }
    }
    return result
}

@Suppress("detekt.LongParameterList")
private fun buildLiveEndpoint(
    samples: List<Int>,
    maxBpm: Int,
    resting: Int?,
    zones: HeartZoneColors,
    surfaceColor: Color,
    leftInset: Float,
    step: Float,
    yFor: (Float) -> Float,
    glowRadius: Float,
    ringRadius: Float,
    coreRadius: Float,
): LiveEndpoint? {
    var lastIndex = -1
    for (i in samples.lastIndex downTo 0) {
        if (samples[i] in HeartRate.BPM_RANGE) {
            lastIndex = i
            break
        }
    }
    if (lastIndex < 0) return null
    val bpm = samples[lastIndex]
    val zoneColor = zones.of(heartRateZone(bpm, maxBpm, resting))
    return LiveEndpoint(
        center = Offset(leftInset + lastIndex * step, yFor(bpm.toFloat())),
        dotColor = zoneColor,
        glowColor = zoneColor.copy(alpha = LIVE_DOT_GLOW_ALPHA),
        ringColor = surfaceColor,
        glowRadius = glowRadius,
        ringRadius = ringRadius,
        coreRadius = coreRadius,
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
private const val ZONE_BAND_ALPHA = 0.045f
private const val THRESHOLD_LINE_ALPHA = 0.18f
private const val THRESHOLD_LINE_DP = 0.75f
private const val THRESHOLD_COUNT = 4
private const val MARK_ALPHA = 0.08f
private const val STROKE_CASING_EXTRA_DP = 1.5f
private const val STROKE_CASING_ALPHA = 0.65f
private const val LIVE_DOT_GLOW_RADIUS_DP = 6.5f
private const val LIVE_DOT_RING_RADIUS_DP = 4.0f
private const val LIVE_DOT_CORE_RADIUS_DP = 2.6f
private const val LIVE_DOT_GLOW_ALPHA = 0.28f
private const val MIN_VISIBLE_SHARE = 0.04f

@PreviewFormFactors
@Composable
private fun HeartRateChartPreview() {
    val samples = listOf(108, 118, 132, 146, 158, 168, 174, 162, 142, 155, 171, 178, 150, 128)
    val marks = listOf(0.2f..0.5f, 0.65f..0.9f)
    IntervalTheme {
        Column(
            Modifier.background(IntervalTheme.palette.surface).padding(Spacing.space16),
            verticalArrangement = Arrangement.spacedBy(Spacing.space12),
        ) {
            HeartRateChart(samples, marks = marks)
            ZoneBreakdown(samples)
        }
    }
}
