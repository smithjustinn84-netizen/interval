package dev.justinsmith.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.justinsmith.interval.platform.ClassSensor
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.LOW_SENSOR_BATTERY
import dev.justinsmith.interval.platform.LiveHeartRate
import dev.justinsmith.interval.platform.LiveHeartRateState
import dev.justinsmith.interval.platform.SensorStatus
import dev.justinsmith.interval.platform.heartRateZone
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.sensor_sheet_battery
import dev.justinsmith.interval.resources.sensor_sheet_battery_low
import dev.justinsmith.interval.resources.sensor_sheet_looking
import dev.justinsmith.interval.resources.sensor_sheet_no_bluetooth
import dev.justinsmith.interval.resources.sensor_sheet_no_contact
import dev.justinsmith.interval.resources.sensor_sheet_recording
import dev.justinsmith.interval.resources.sensor_sheet_settings
import dev.justinsmith.interval.resources.sensor_sheet_settings_hint
import dev.justinsmith.interval.resources.sensor_sheet_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * [content] with the paired heart-rate sensor on every tab's title bar ([LocalSensorPulse], which the gear
 * draws): its heart rate live, and a tap away the sheet that says the rest ([SensorSheet]), whose row opens
 * Settings ([onOpenSettings]). The zones go by [maxHeartRate], null for the default.
 *
 * The heart rate is streamed for as long as the app is on show with a sensor paired, and let go as it leaves
 * the screen: a workout or a Lift session holds the stream for itself, so one under way reads on. A sensor
 * forgotten drops every hold, which is why the hold is taken again whenever one is paired.
 */
@Composable
internal fun WithSensorPulse(maxHeartRate: Int?, onOpenSettings: () -> Unit, content: @Composable () -> Unit) {
    val live = koinInject<LiveHeartRate>()
    val sensor = koinInject<ClassSensor>()
    val status by sensor.status.collectAsStateWithLifecycle()
    val battery by sensor.battery.collectAsStateWithLifecycle()
    val reading by live.reading.collectAsStateWithLifecycle()
    val holder = remember { Any() }
    val paired = status is SensorStatus.Paired
    LifecycleStartEffect(live, paired) {
        if (paired) live.start(holder)
        onStopOrDispose { if (paired) live.stop(holder) }
    }
    var open by rememberSaveable { mutableStateOf(false) }
    val show = remember { { open = true } }
    val name = when (val it = status) {
        is SensorStatus.Paired -> it.name
        is SensorStatus.Recording -> it.name
        SensorStatus.Unavailable, SensorStatus.Unpaired -> null
    }
    val pulse = name?.let {
        SensorPulse(
            state = reading,
            name = it,
            maxBpm = maxHeartRate ?: DEFAULT_MAX_BPM,
            recording = status is SensorStatus.Recording,
            lowBattery = battery.isLow,
            onOpen = show,
        )
    }
    CompositionLocalProvider(LocalSensorPulse provides pulse, content = content)
    if (open && pulse != null) SensorSheet(pulse, battery, onOpenSettings, onDismiss = { open = false })
}

private val Int?.isLow: Boolean get() = this != null && this < LOW_SENSOR_BATTERY

/**
 * The paired sensor in full, from a tap on its pulse in a title bar: its name, the heart rate with its zone
 * by number and name (or, with none read, why not and what to do about it), the [battery] as the sensor last
 * said it, and the way to where it is paired and forgotten. [onOpenSettings] is heard once the sheet is away.
 */
@Composable
internal fun SensorSheet(pulse: SensorPulse, battery: Int?, onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    var toSettings by remember { mutableStateOf(false) }
    IntervalSheet(
        title = stringResource(Res.string.sensor_sheet_title),
        onDismiss = {
            onDismiss()
            if (toSettings) onOpenSettings()
        },
    ) { hide ->
        SensorSheetContent(pulse, battery) {
            toSettings = true
            hide()
        }
    }
}

@Composable
private fun ColumnScope.SensorSheetContent(pulse: SensorPulse, battery: Int?, onOpenSettings: () -> Unit) {
    val palette = IntervalTheme.palette
    Text(pulse.name, style = IntervalTheme.type.value, color = palette.text)
    val bpm = pulse.bpm
    if (bpm != null && !pulse.recording) {
        val zone = heartRateZone(bpm, pulse.maxBpm, LocalRestingHeartRate.current)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            HeartReading(bpm, zone, onWorkoutGround = false)
            HeartZoneBars(zone, onWorkoutGround = false)
        }
    } else {
        Text(
            stringResource(
                when {
                    pulse.recording -> Res.string.sensor_sheet_recording
                    pulse.state is LiveHeartRateState.Reading -> Res.string.sensor_sheet_no_contact
                    pulse.state.isSearching -> Res.string.sensor_sheet_looking
                    else -> Res.string.sensor_sheet_no_bluetooth
                },
            ),
            style = IntervalTheme.type.label,
            color = palette.text,
        )
    }
    if (battery != null) {
        val low = battery < LOW_SENSOR_BATTERY
        Text(
            stringResource(if (low) Res.string.sensor_sheet_battery_low else Res.string.sensor_sheet_battery, battery),
            style = IntervalTheme.type.meta,
            color = if (low) palette.danger else palette.textDim,
        )
    }
    ListCard {
        LinkRow(
            stringResource(Res.string.sensor_sheet_settings),
            stringResource(Res.string.sensor_sheet_settings_hint),
            onClick = onOpenSettings,
        )
    }
}

/** Being looked for: the stream is up and has no connection yet, or lost the one it had. */
private val LiveHeartRateState.isSearching: Boolean
    get() = this == LiveHeartRateState.Connecting || this == LiveHeartRateState.Disconnected

@PreviewFormFactors
@Composable
private fun SensorSheetPreview() {
    IntervalTheme {
        Column(
            Modifier.background(IntervalTheme.palette.surface).padding(Spacing.space24),
            verticalArrangement = Arrangement.spacedBy(Spacing.space16),
        ) {
            SensorSheetContent(
                SensorPulse(LiveHeartRateState.Reading(bpm = 142), "Polar Sense 1A2B3C4D"),
                battery = 64,
            ) {
            }
            SensorSheetContent(SensorPulse(LiveHeartRateState.Disconnected, "Polar H10 5E6F7A8B"), battery = 12) {}
        }
    }
}
