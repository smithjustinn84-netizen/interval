package dev.justinsmith.interval.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.heart_rate_connecting
import dev.justinsmith.core.resources.heart_rate_disconnected
import dev.justinsmith.core.resources.heart_rate_scale_cd
import dev.justinsmith.core.resources.heart_rate_unit
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.platform.LiveHeartRateState
import dev.justinsmith.interval.platform.heartRateZone
import dev.justinsmith.interval.platform.inUse
import org.jetbrains.compose.resources.stringResource

/**
 * The live heart rate as [HeartZoneScale] reads it out, over the heart rate so far as a line in place of the
 * scale's bars: [samples] drawn across the zones' bands, each of [marks] (a work interval) shaded behind it. It
 * takes the room the scale does, so the two swap under the workout's ring without moving it. Draws nothing
 * unless the sensor is [inUse]. On the workout's ground only.
 */
@Composable
fun LiveHeartTrace(
    state: LiveHeartRateState,
    samples: List<Int>,
    modifier: Modifier = Modifier,
    maxBpm: Int = DEFAULT_MAX_BPM,
    marks: List<ClosedFloatingPointRange<Float>> = emptyList(),
) {
    if (!state.inUse) return
    val reading = (state as? LiveHeartRateState.Reading)?.takeIf { it.contact }
    Column(
        modifier.widthIn(max = Sizes.zoneScale).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = Sizes.zoneScaleReadout), contentAlignment = Alignment.Center) {
            if (reading != null) {
                ReadingRow(reading.bpm, heartRateZone(reading.bpm, maxBpm, LocalRestingHeartRate.current))
            } else {
                StatusRow(connecting = state == LiveHeartRateState.Connecting, lastBpm = samples.lastRead())
            }
        }
        val height = Sizes.zoneScaleMarkerHeight + Spacing.space4 + Sizes.zoneScaleBounds
        Box(Modifier.fillMaxWidth().height(height).clip(Corners.radius12)) {
            HeartRateChart(samples, maxBpm = maxBpm, onWorkoutGround = true, marks = marks, height = height)
        }
    }
}

/**
 * The live heart rate on a card of the app's ground, as a Lift session shows it among its numbers: the beats a
 * minute, the zone by name and number, and the session's heart rate so far as a line over the zones' bands.
 * Without a reading it says why in place of the number, the line staying. Draws nothing unless the sensor is
 * [inUse].
 */
@Composable
fun HeartCard(
    state: LiveHeartRateState,
    samples: List<Int>,
    modifier: Modifier = Modifier,
    maxBpm: Int = DEFAULT_MAX_BPM,
) {
    if (!state.inUse) return
    val palette = IntervalTheme.palette
    val reading = (state as? LiveHeartRateState.Reading)?.takeIf { it.contact }
    Column(
        modifier
            .fillMaxWidth()
            .clip(Corners.radius18)
            .background(palette.surface)
            .border(Sizes.hairline, palette.line, Corners.radius18)
            .padding(Spacing.space16),
        verticalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        if (reading != null) {
            CardReading(reading.bpm, maxBpm)
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    IntervalIcons.Heart,
                    contentDescription = null,
                    tint = palette.textDim,
                    modifier = Modifier.size(Sizes.icon),
                )
                Text(
                    stringResource(
                        if (state == LiveHeartRateState.Connecting) {
                            Res.string.heart_rate_connecting
                        } else {
                            Res.string.heart_rate_disconnected
                        },
                    ),
                    style = IntervalTheme.type.section,
                    color = palette.textDim,
                )
            }
        }
        if (samples.count { it in HeartRate.BPM_RANGE } >= 2) {
            Box(Modifier.fillMaxWidth().clip(Corners.radius12)) {
                HeartRateChart(samples, maxBpm = maxBpm, height = Sizes.heartTrace)
            }
        } else {
            Spacer(Modifier.height(Sizes.heartTrace))
        }
    }
}

/** The card's reading: the heart beating in its zone's colour, the number, and the zone named and tagged. */
@Composable
private fun CardReading(bpm: Int, maxBpm: Int) {
    val palette = IntervalTheme.palette
    val zone = heartRateZone(bpm, maxBpm, LocalRestingHeartRate.current)
    val zoneColor by animateColorAsState(
        palette.heartZones.of(zone),
        tween(Motion.STATE_MILLIS, easing = StandardEasing),
        label = "cardZone",
    )
    val scale = rememberHeartPulseScale(bpm)
    val name = heartRateZoneName(zone)
    val description = stringResource(Res.string.heart_rate_scale_cd, bpm, zone.number, name)
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
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
                Text("$bpm", Modifier.alignByBaseline(), style = IntervalTheme.type.stat, color = palette.text)
                Text(
                    stringResource(Res.string.heart_rate_unit),
                    Modifier.alignByBaseline(),
                    style = IntervalTheme.type.caption,
                    color = palette.textDim,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, style = IntervalTheme.type.label, color = palette.text)
            ZoneTag(zone, zoneColor, onWorkoutGround = false)
        }
    }
}

/** The last heart rate among these samples that was read, for a signal lost. */
private fun List<Int>.lastRead(): Int? = lastOrNull { it in HeartRate.BPM_RANGE }

/** A workout's heart rate climbing through two work intervals, for the previews. */
internal val previewTrace = listOf(
    96, 104, 118, 131, 142, 151, 157, 160, 149, 137, 128, 139, 150, 158, 163, 166, 154, 141, 133, 144, 153, 158,
)

@PreviewFormFactors
@Composable
private fun LiveHeartTracePreview() {
    val marks = listOf(0.1f..0.36f, 0.5f..0.77f, 0.9f..1f)
    IntervalTheme {
        Column(
            Modifier.background(IntervalTheme.workout.ground).padding(Spacing.space16),
            verticalArrangement = Arrangement.spacedBy(Spacing.space24),
        ) {
            LiveHeartTrace(LiveHeartRateState.Reading(bpm = 158), previewTrace, marks = marks)
            LiveHeartTrace(LiveHeartRateState.Disconnected, previewTrace)
        }
    }
}

@PreviewFormFactors
@Composable
private fun HeartCardPreview() {
    IntervalTheme {
        Column(
            Modifier.background(IntervalTheme.palette.background).padding(Spacing.space16),
            verticalArrangement = Arrangement.spacedBy(Spacing.space12),
        ) {
            HeartCard(LiveHeartRateState.Reading(bpm = 142), previewTrace)
            HeartCard(LiveHeartRateState.Connecting, emptyList())
        }
    }
}
