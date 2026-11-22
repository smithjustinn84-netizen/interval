package dev.justinsmith.interval.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.sensor_pulse_idle_cd
import dev.justinsmith.core.resources.sensor_pulse_low_battery_cd
import dev.justinsmith.core.resources.sensor_pulse_reading_cd
import dev.justinsmith.core.resources.sensor_pulse_rec
import dev.justinsmith.core.resources.sensor_pulse_recording_cd
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.LiveHeartRateState
import dev.justinsmith.interval.platform.heartRateZone
import org.jetbrains.compose.resources.stringResource

/**
 * The paired heart-rate sensor as every tab's title bar shows it: what it reads now ([state], its zones cut
 * from [maxBpm]), the [name] it goes by, whether it is [recording] a class (it streams nothing then) or its
 * battery is low ([lowBattery]), and [onOpen], what a tap on it does: the sensor's own sheet.
 */
@Immutable
data class SensorPulse(
    val state: LiveHeartRateState,
    val name: String,
    val maxBpm: Int = DEFAULT_MAX_BPM,
    val recording: Boolean = false,
    val lowBattery: Boolean = false,
    val onOpen: () -> Unit = {},
) {
    /** The heart rate read now, null with none: not connected, or connected with no heart to read. */
    val bpm: Int? get() = (state as? LiveHeartRateState.Reading)?.takeIf { it.contact }?.bpm
}

/**
 * The paired sensor for [SettingsAction] to draw before its gear, so it is on every tab without any of them
 * knowing of a sensor; null, as it is with none paired and on a platform with none, draws nothing. The app
 * provides it once, over everything. Only the gear reads it, so a heart rate that changes each second
 * composes nothing else again.
 */
val LocalSensorPulse = compositionLocalOf<SensorPulse?> { null }

/**
 * The sensor in a title bar: read, as the heart rate reads everywhere, a dot beating in its zone's colour, the
 * number and a dim BPM; not read, a dim heart standing still, with no words, since the sensor is off for most
 * of a day; recording a class, that heart and "Rec". A low battery puts a red dot on the badge's corner. The
 * whole of it is the tap that opens the sensor's sheet, as wide as an icon's at least and as tall, its glyphs
 * level with the title's capitals as an [IconAction]'s are. Read by a screen reader as what it shows and the
 * sensor's name, never as each beat.
 */
@Composable
fun SensorPulseBadge(pulse: SensorPulse, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val bpm = pulse.bpm
    val zone = bpm?.let { heartRateZone(it, pulse.maxBpm, LocalRestingHeartRate.current) }
    val description = listOfNotNull(
        when {
            pulse.recording -> stringResource(Res.string.sensor_pulse_recording_cd, pulse.name)

            bpm != null && zone != null ->
                stringResource(Res.string.sensor_pulse_reading_cd, bpm, zone.number, pulse.name)

            else -> stringResource(Res.string.sensor_pulse_idle_cd, pulse.name)
        },
        stringResource(Res.string.sensor_pulse_low_battery_cd).takeIf { pulse.lowBattery },
    ).joinToString()
    val heart by animateColorAsState(
        zone?.let(palette.heartZones::of) ?: palette.textDim,
        tween(Motion.STATE_MILLIS, easing = StandardEasing),
        label = "sensorPulse",
    )
    Box(
        modifier
            .height(Sizes.iconButton)
            .defaultMinSize(minWidth = Sizes.iconButton)
            .clickable(interaction, indication = null, role = Role.Button, onClick = pulse.onOpen)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick {
                    pulse.onOpen()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.offset(y = LocalTitleBarGlyphShift.current)) {
            Row(
                Modifier
                    .pressScale(interaction)
                    .clip(Corners.full)
                    .background(if (bpm != null) palette.surfaceVariant else Color.Transparent)
                    .indication(interaction, LocalIndication.current)
                    .padding(horizontal = Spacing.space8, vertical = Spacing.space4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
            ) {
                if (bpm != null && !pulse.recording) {
                    ZoneDot(heart, bpm)
                    BadgeBpm(bpm, palette.text, palette.textDim)
                } else {
                    Icon(IntervalIcons.Heart, null, Modifier.size(Sizes.sensorPulseHeart), tint = heart)
                    if (pulse.recording) {
                        Text(
                            stringResource(Res.string.sensor_pulse_rec),
                            style = IntervalTheme.type.caption,
                            color = palette.textDim,
                        )
                    }
                }
            }
            if (pulse.lowBattery) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(Sizes.sensorPulseDot)
                        .clip(CircleShape)
                        .background(palette.danger),
                )
            }
        }
    }
}

@PreviewFormFactors
@Composable
private fun SensorPulseBadgePreview() {
    IntervalTheme {
        Row(Modifier.background(IntervalTheme.palette.background)) {
            SensorPulseBadge(SensorPulse(LiveHeartRateState.Reading(bpm = 72), "Polar Sense 1A2B3C4D"))
            SensorPulseBadge(
                SensorPulse(LiveHeartRateState.Reading(bpm = 168), "Polar Sense 1A2B3C4D", lowBattery = true),
            )
            SensorPulseBadge(SensorPulse(LiveHeartRateState.Disconnected, "Polar Sense 1A2B3C4D"))
            SensorPulseBadge(SensorPulse(LiveHeartRateState.Idle, "Polar Sense 1A2B3C4D", lowBattery = true))
            SensorPulseBadge(SensorPulse(LiveHeartRateState.Unavailable, "Polar Sense 1A2B3C4D", recording = true))
        }
    }
}
