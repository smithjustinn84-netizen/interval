package dev.justinsmith.interval.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.heart_rate_connecting
import dev.justinsmith.core.resources.heart_rate_disconnected
import dev.justinsmith.core.resources.heart_rate_last
import dev.justinsmith.core.resources.heart_rate_scale_cd
import dev.justinsmith.core.resources.heart_rate_unit
import dev.justinsmith.core.resources.heart_rate_zone_short
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.HeartRateZone
import dev.justinsmith.interval.platform.LiveHeartRateState
import dev.justinsmith.interval.platform.heartRateZone
import dev.justinsmith.interval.platform.inUse
import dev.justinsmith.interval.platform.zoneFloor
import dev.justinsmith.interval.platform.zoneScaleFraction
import org.jetbrains.compose.resources.stringResource

/**
 * The live heart rate as the workout shows it under its ring, to be read from across a gym: the beats a
 * minute, the zone by name and number, and the five zones as a scale a fifth each, the one the heart is in
 * lit in its colour, a marker where in it, and under its two ends the heart rates at which the zone changes.
 * A zone is never told by its colour alone, which at Zone 4 is close to the work phase's.
 *
 * With no reading yet, or the sensor out of reach, the scale stays in its place, unlit, so the ring over it
 * doesn't move; a lost signal says the last heart rate read. Draws nothing unless the sensor is [inUse].
 * On the workout's ground only.
 */
@Composable
fun HeartZoneScale(state: LiveHeartRateState, modifier: Modifier = Modifier, maxBpm: Int = DEFAULT_MAX_BPM) {
    if (!state.inUse) return
    val reading = state as? LiveHeartRateState.Reading
    var lastBpm by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(reading?.bpm) { reading?.let { lastBpm = it.bpm } }
    val zone = reading?.let { heartRateZone(it.bpm, maxBpm) }

    Column(
        modifier.widthIn(max = Sizes.zoneScale).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = Sizes.zoneScaleReadout), contentAlignment = Alignment.Center) {
            if (reading != null && zone != null) {
                ReadingRow(reading.bpm, zone)
            } else {
                StatusRow(connecting = state == LiveHeartRateState.Connecting, lastBpm = lastBpm)
            }
        }
        ZoneBars(zone, fraction = reading?.let { zoneScaleFraction(it.bpm, maxBpm) } ?: 0f, maxBpm = maxBpm)
    }
}

@Composable
private fun ReadingRow(bpm: Int, zone: HeartRateZone) {
    val colors = IntervalTheme.workout
    val zoneColor by animateColorAsState(
        colors.heartZones.of(zone),
        tween(Motion.STATE_MILLIS, easing = StandardEasing),
        label = "zone",
    )
    val scale = rememberHeartPulseScale(bpm)
    val name = heartRateZoneName(zone)
    val description = stringResource(Res.string.heart_rate_scale_cd, bpm, zone.number, name)
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                IntervalIcons.Heart,
                contentDescription = null,
                tint = zoneColor,
                modifier = Modifier.size(Sizes.icon).graphicsLayer {
                    val s = scale()
                    scaleX = s
                    scaleY = s
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space4)) {
                Text("$bpm", Modifier.alignByBaseline(), style = IntervalTheme.type.stat, color = colors.onGround)
                Text(
                    stringResource(Res.string.heart_rate_unit),
                    Modifier.alignByBaseline(),
                    style = IntervalTheme.type.caption,
                    color = colors.onGroundDim,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, style = IntervalTheme.type.section, color = colors.onGround)
            Text(
                stringResource(Res.string.heart_rate_zone_short, zone.number),
                style = IntervalTheme.type.control,
                color = colors.onFill(zoneColor),
                modifier = Modifier
                    .clip(Corners.radius12)
                    .background(zoneColor)
                    .padding(horizontal = Spacing.space12, vertical = Spacing.space4),
            )
        }
    }
}

/** Why there is no reading, and the last there was once the signal is lost. */
@Composable
private fun StatusRow(connecting: Boolean, lastBpm: Int?) {
    val dim = IntervalTheme.workout.onGroundDim
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(IntervalIcons.Heart, contentDescription = null, tint = dim, modifier = Modifier.size(Sizes.icon))
            Text(
                stringResource(
                    if (connecting) Res.string.heart_rate_connecting else Res.string.heart_rate_disconnected,
                ),
                style = IntervalTheme.type.section,
                color = dim,
            )
        }
        if (!connecting && lastBpm != null) {
            Text(stringResource(Res.string.heart_rate_last, lastBpm), style = IntervalTheme.type.meta, color = dim)
        }
    }
}

/**
 * The five zones a bar each, [zone]'s lit and taller, the marker at [fraction] of the way along them, and
 * under the lit bar's ends the heart rates the zone starts at and gives way at. Unlit and bare without a zone.
 */
@Composable
private fun ZoneBars(zone: HeartRateZone?, fraction: Float, maxBpm: Int) {
    val colors = IntervalTheme.workout
    val fills = HeartRateZone.entries.map { each ->
        animateColorAsState(
            if (each == zone) colors.heartZones.of(each) else colors.upcoming,
            tween(Motion.STATE_MILLIS, easing = StandardEasing),
            label = "zoneBar",
        )
    }
    val at by animateFloatAsState(fraction, tween(Motion.STATE_MILLIS, easing = StandardEasing), label = "zoneMarker")
    val measurer = rememberTextMeasurer()
    val boundStyle = IntervalTheme.type.caption.copy(color = colors.onGroundDim)
    val bounds = remember(zone, maxBpm, measurer, boundStyle) {
        zone?.let { lit ->
            val above = HeartRateZone.entries.getOrNull(lit.ordinal + 1)
            listOf(lit.ordinal to zoneFloor(lit, maxBpm), lit.ordinal + 1 to above?.let { zoneFloor(it, maxBpm) })
                .mapNotNull { (edge, bpm) -> bpm?.let { edge to measurer.measure("$it", boundStyle) } }
        }.orEmpty()
    }
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(Sizes.zoneScaleMarkerHeight + Spacing.space4 + Sizes.zoneScaleBounds)
            .drawBehind {
                val bars = ZoneBarGeometry(size.width, Spacing.space4.toPx())
                fills.forEachIndexed { index, fill ->
                    val lit = index == zone?.ordinal
                    drawBar(
                        bars,
                        index,
                        if (lit) Sizes.zoneScaleBarLit.toPx() else Sizes.zoneScaleBar.toPx(),
                        fill.value,
                    )
                }
                if (zone != null) drawMarker(bars.xAt(at), colors.onGround, colors.ground)
                bounds.forEach { (edge, text) -> drawBound(bars, edge, text) }
            },
    )
}

/** Where the five bars of a scale [width] wide stand, [gap] apart. */
private class ZoneBarGeometry(private val width: Float, private val gap: Float) {
    val bar: Float = (width - gap * (ZONES - 1)) / ZONES

    fun start(index: Int): Float = index * (bar + gap)

    /** The line between the bar before [edge] and the one after it: the middle of their gap. */
    fun edge(edge: Int): Float = (start(edge) - gap / 2).coerceIn(0f, width)

    /** [fraction] of the way along the zones, a fifth each, never in a gap between two. */
    fun xAt(fraction: Float): Float {
        val along = fraction.coerceIn(0f, 1f) * ZONES
        val index = along.toInt().coerceAtMost(ZONES - 1)
        return start(index) + (along - index) * bar
    }
}

private fun DrawScope.drawBar(bars: ZoneBarGeometry, index: Int, height: Float, color: Color) {
    drawRoundRect(
        color,
        topLeft = Offset(bars.start(index), (Sizes.zoneScaleMarkerHeight.toPx() - height) / 2),
        size = Size(bars.bar, height),
        cornerRadius = CornerRadius(height / 2),
    )
}

/** The marker of the heart rate, with an edge of the ground round it so it parts from the bar it crosses. */
private fun DrawScope.drawMarker(x: Float, color: Color, ground: Color) {
    val width = Sizes.zoneScaleMarkerWidth.toPx()
    val height = Sizes.zoneScaleMarkerHeight.toPx()
    val edge = Sizes.zoneScaleMarkerEdge.toPx()
    val left = (x - width / 2).coerceIn(0f, size.width - width)
    drawRoundRect(
        ground,
        topLeft = Offset(left - edge, 0f),
        size = Size(width + edge * 2, height),
        cornerRadius = CornerRadius(edge),
    )
    drawRoundRect(color, Offset(left, 0f), Size(width, height), CornerRadius(width / 2))
}

/** A zone's end in beats a minute, centred under the line it is, kept inside the scale. */
private fun DrawScope.drawBound(bars: ZoneBarGeometry, edge: Int, text: TextLayoutResult) {
    val left = (bars.edge(edge) - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
    drawText(text, topLeft = Offset(left, Sizes.zoneScaleMarkerHeight.toPx() + Spacing.space4.toPx()))
}

private const val ZONES = 5

@PreviewFormFactors
@Composable
private fun HeartZoneScalePreview() {
    IntervalTheme {
        Column(
            Modifier.background(IntervalTheme.workout.ground).padding(Spacing.space16),
            verticalArrangement = Arrangement.spacedBy(Spacing.space24),
        ) {
            HeartZoneScale(LiveHeartRateState.Reading(bpm = 158))
            HeartZoneScale(LiveHeartRateState.Reading(bpm = 104))
            HeartZoneScale(LiveHeartRateState.Reading(bpm = 181))
            HeartZoneScale(LiveHeartRateState.Connecting)
            HeartZoneScale(LiveHeartRateState.Disconnected)
        }
    }
}
