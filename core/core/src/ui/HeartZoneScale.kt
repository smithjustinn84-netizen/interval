package dev.justinsmith.interval.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.heart_rate_connecting
import dev.justinsmith.core.resources.heart_rate_disconnected
import dev.justinsmith.core.resources.heart_rate_last
import dev.justinsmith.core.resources.heart_rate_scale_cd
import dev.justinsmith.core.resources.heart_rate_unit_caps
import dev.justinsmith.core.resources.heart_rate_zone_named
import dev.justinsmith.core.resources.heart_rate_zone_number
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.HeartRateZone
import dev.justinsmith.interval.platform.LiveHeartRateState
import dev.justinsmith.interval.platform.heartRateZone
import dev.justinsmith.interval.platform.inUse
import org.jetbrains.compose.resources.stringResource

/**
 * The live heart rate as the workout shows it under its ring, to be read from across a gym: a dot in the zone's
 * colour beating with the heart, the beats a minute, the zone by number and name, and the five zones as bars a
 * fifth each, every one in its own colour faint and the one the heart is in lit and a little taller. A zone is
 * never told by its colour alone, which at Zone 4 is close to the work phase's.
 *
 * With no reading yet, or the sensor out of reach, the bars stay in their place, faint, so the ring over them
 * doesn't move; a lost signal says the last heart rate read. Draws nothing unless the sensor is [inUse].
 * On the workout's ground only.
 */
@Composable
fun HeartZoneScale(state: LiveHeartRateState, modifier: Modifier = Modifier, maxBpm: Int = DEFAULT_MAX_BPM) {
    if (!state.inUse) return
    // Connected with no heart to read is no signal, the last reading beside it.
    val reading = (state as? LiveHeartRateState.Reading)?.takeIf { it.contact }
    var lastBpm by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(reading?.bpm) { reading?.let { lastBpm = it.bpm } }
    val zone = reading?.let { heartRateZone(it.bpm, maxBpm, LocalRestingHeartRate.current) }

    Column(
        modifier.widthIn(max = Sizes.zoneScale).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = Sizes.zoneScaleReadout), contentAlignment = Alignment.Center) {
            if (reading != null && zone != null) {
                HeartReading(reading.bpm, zone, onWorkoutGround = true)
            } else {
                StatusRow(connecting = state == LiveHeartRateState.Connecting, lastBpm = lastBpm)
            }
        }
        HeartZoneBars(zone, onWorkoutGround = true)
    }
}

/**
 * The heart rate read out as one line: a dot in [zone]'s colour beating with it (still, unless [beating]), the
 * beats a minute over BPM, and the zone by number and name dim at the right, in the workout's colours
 * [onWorkoutGround] or the app's. Where the line [keepsHeight] (under the workout's ring, so the ring never moves)
 * large text says the zone by number alone and its words grow no further than large text begins, since squeezed
 * they break mid-word ("Zon / e 4"). Anywhere else large text puts the zone, in full, on a line of its own under
 * the number. Either way it is read as one description.
 */
@Composable
fun HeartReading(
    bpm: Int,
    zone: HeartRateZone,
    onWorkoutGround: Boolean,
    modifier: Modifier = Modifier,
    beating: Boolean = true,
    keepsHeight: Boolean = onWorkoutGround,
) {
    val text = if (onWorkoutGround) IntervalTheme.workout.onGround else IntervalTheme.palette.text
    val dim = if (onWorkoutGround) IntervalTheme.workout.onGroundDim else IntervalTheme.palette.textDim
    val zones = if (onWorkoutGround) IntervalTheme.workout.heartZones else IntervalTheme.palette.heartZones
    val zoneColor by animateColorAsState(
        zones.of(zone),
        tween(Motion.STATE_MILLIS, easing = StandardEasing),
        label = "zone",
    )
    // A heart rate read now beats; one summed up afterwards (a workout's average) holds still.
    val scale = if (beating) rememberHeartPulseScale(bpm) else STILL
    val name = heartRateZoneName(zone)
    val description = stringResource(Res.string.heart_rate_scale_cd, bpm, zone.number, name)
    val large = isLargeText()
    val zoneWords = if (large && keepsHeight) {
        stringResource(Res.string.heart_rate_zone_number, zone.number)
    } else {
        stringResource(Res.string.heart_rate_zone_named, zone.number, name)
    }
    val reading = @Composable {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(Sizes.zoneScaleDot)
                    .graphicsLayer {
                        val s = scale()
                        scaleX = s
                        scaleY = s
                    }
                    .clip(Corners.full)
                    .background(zoneColor),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                Text("$bpm", Modifier.alignByBaseline(), style = IntervalTheme.type.stat, color = text)
                Text(
                    stringResource(Res.string.heart_rate_unit_caps),
                    Modifier.alignByBaseline(),
                    style = IntervalTheme.type.body,
                    color = dim,
                )
            }
        }
    }
    val described = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }
    when {
        keepsHeight -> TextScaleAtMost(LARGE_FONT_SCALE) {
            Row(
                described,
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                reading()
                JoinedText(zoneWords, style = IntervalTheme.type.body, color = dim)
            }
        }

        large -> Column(described, verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            reading()
            JoinedText(zoneWords, style = IntervalTheme.type.body, color = dim)
        }

        else -> Row(
            described,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            reading()
            JoinedText(zoneWords, style = IntervalTheme.type.body, color = dim)
        }
    }
}

/** Why there is no reading, and the last there was once the signal is lost. */
@Composable
internal fun StatusRow(connecting: Boolean, lastBpm: Int?) {
    // As the reading's row: the card keeps its height.
    TextScaleAtMost(LARGE_FONT_SCALE) {
        val dim = IntervalTheme.workout.onGroundDim
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(Sizes.zoneScaleDot).clip(Corners.full).background(dim))
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
}

/**
 * The five zones a bar each in its own colour, faint, [zone]'s lit and a little taller; all faint without one.
 * In the workout's colours [onWorkoutGround], else the app's.
 */
@Composable
fun HeartZoneBars(zone: HeartRateZone?, onWorkoutGround: Boolean, modifier: Modifier = Modifier) {
    val zones = if (onWorkoutGround) IntervalTheme.workout.heartZones else IntervalTheme.palette.heartZones
    val fills = HeartRateZone.entries.map { each ->
        val full = zones.of(each)
        animateColorAsState(
            if (each == zone) full else full.copy(alpha = Opacity.ZONE_UNLIT),
            tween(Motion.STATE_MILLIS, easing = StandardEasing),
            label = "zoneBar",
        )
    }
    Spacer(
        modifier
            .fillMaxWidth()
            .height(Sizes.zoneScaleBand)
            .drawBehind {
                val gap = Spacing.space8.toPx()
                val width = (size.width - gap * (ZONES - 1)) / ZONES
                fills.forEachIndexed { index, fill ->
                    val height = (if (index == zone?.ordinal) Sizes.zoneScaleBarLit else Sizes.zoneScaleBar).toPx()
                    drawRoundRect(
                        fill.value,
                        topLeft = Offset(index * (width + gap), (size.height - height) / 2),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(height / 2),
                    )
                }
            },
    )
}

private const val ZONES = 5

private val STILL: () -> Float = { 1f }

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
