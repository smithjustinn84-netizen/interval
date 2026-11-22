package dev.justinsmith.interval.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.sensor_pulse_idle_cd
import dev.justinsmith.core.resources.sensor_pulse_low_battery_cd
import dev.justinsmith.core.resources.sensor_pulse_reading_cd
import dev.justinsmith.core.resources.sensor_pulse_rec
import dev.justinsmith.core.resources.sensor_pulse_recording_cd
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.HeartRateZone
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
 * The sensor in a title bar: read, as the heart rate reads everywhere, a procedural pulse emitter beating in its
 * zone's colour with concentric acoustic ripples scaled by zone, the number and a dim BPM; not read, a dim heart
 * framed by a calm breathing ring with no words, since the sensor is off for most of a day; recording a class,
 * that heart and "Rec". A low battery puts a red dot on the badge's corner. The whole of it is the tap that opens
 * the sensor's sheet, as wide as an icon's at least and as tall, its glyphs level with the title's capitals as an
 * [IconAction]'s are. Read by a screen reader as what it shows and the sensor's name, never as each beat.
 */
@Composable
fun SensorPulseBadge(pulse: SensorPulse, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val bpm = pulse.bpm
    val zone = bpm?.let { heartRateZone(it, pulse.maxBpm, LocalRestingHeartRate.current) }
    val description = sensorPulseDescription(pulse, bpm, zone)
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
            SensorPulsePill(
                bpm = bpm,
                zone = zone,
                recording = pulse.recording,
                interaction = interaction,
            )
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

@Composable
private fun SensorPulsePill(
    bpm: Int?,
    zone: HeartRateZone?,
    recording: Boolean,
    interaction: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    val activeReading = bpm != null && !recording
    val profile = pulseZoneProfile(bpm, zone, recording)
    val heart by animateColorAsState(
        if (activeReading && zone != null) palette.heartZones.of(zone) else palette.textDim,
        tween(Motion.STATE_MILLIS, easing = StandardEasing),
        label = "sensorPulse",
    )
    val waveSpan by animateFloatAsState(
        profile.waveSpan,
        tween(Motion.ENTER_MILLIS, easing = StandardEasing),
        label = "sensorPulseWaveSpan",
    )
    val intensity by animateFloatAsState(
        profile.intensity,
        tween(Motion.ENTER_MILLIS, easing = StandardEasing),
        label = "sensorPulseIntensity",
    )
    val active by animateFloatAsState(
        profile.active,
        tween(Motion.CONTENT_MILLIS, easing = StandardEasing),
        label = "sensorPulseActive",
    )
    val idle = LocalIdle.current
    val phase = rememberPulsePhase(profile.beatsPerSecond, paused = !activeReading && idle())
    val program = remember { shaderProgram(SENSOR_PULSE_SHADER) }
    val contentPadding = when {
        activeReading -> PaddingValues(horizontal = Spacing.space8, vertical = Spacing.space4)
        recording -> PaddingValues(horizontal = Spacing.space8, vertical = RECORDING_VERTICAL_PADDING)
        else -> PaddingValues(Spacing.space8)
    }
    val centerX = Spacing.space8 + (if (activeReading) Sizes.sensorPulseEmitter else Sizes.sensorPulseHeart) / 2
    Row(
        modifier
            .pressScale(interaction)
            .clip(Corners.full)
            .background(if (activeReading) palette.surfaceVariant else Color.Transparent)
            .sensorPulseWave(
                phase = { phase.floatValue },
                color = { heart },
                waveSpan = { waveSpan },
                intensity = { intensity },
                active = { active },
                centerX = centerX,
                centered = !activeReading && !recording,
                program = program,
            )
            .indication(interaction, LocalIndication.current)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        if (activeReading) {
            Spacer(Modifier.size(Sizes.sensorPulseEmitter))
            BadgeBpm(bpm, palette.text, palette.textDim)
        } else {
            Icon(IntervalIcons.Heart, null, Modifier.size(Sizes.sensorPulseHeart), tint = heart)
            if (recording) {
                Text(
                    stringResource(Res.string.sensor_pulse_rec),
                    style = IntervalTheme.type.caption,
                    color = palette.textDim,
                )
            }
        }
    }
}

@Composable
private fun sensorPulseDescription(pulse: SensorPulse, bpm: Int?, zone: HeartRateZone?): String = listOfNotNull(
    when {
        pulse.recording -> stringResource(Res.string.sensor_pulse_recording_cd, pulse.name)

        bpm != null && zone != null ->
            stringResource(Res.string.sensor_pulse_reading_cd, bpm, zone.number, pulse.name)

        else -> stringResource(Res.string.sensor_pulse_idle_cd, pulse.name)
    },
    stringResource(Res.string.sensor_pulse_low_battery_cd).takeIf { pulse.lowBattery },
).joinToString()

private val RECORDING_VERTICAL_PADDING = 6.dp

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

@PreviewFormFactors
@Composable
private fun SensorPulseZonesPreview() {
    IntervalTheme {
        Column(
            Modifier.background(IntervalTheme.palette.background).padding(Spacing.space12),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                SensorPulseBadge(SensorPulse(LiveHeartRateState.Reading(bpm = 95), "Polar Sense"))
                SensorPulseBadge(SensorPulse(LiveHeartRateState.Reading(bpm = 122), "Polar Sense"))
                SensorPulseBadge(SensorPulse(LiveHeartRateState.Reading(bpm = 142), "Polar Sense"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                SensorPulseBadge(SensorPulse(LiveHeartRateState.Reading(bpm = 162), "Polar Sense"))
                SensorPulseBadge(SensorPulse(LiveHeartRateState.Reading(bpm = 182), "Polar Sense"))
                SensorPulseBadge(SensorPulse(LiveHeartRateState.Disconnected, "Polar Sense"))
            }
        }
    }
}
