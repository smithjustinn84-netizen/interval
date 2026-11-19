package dev.justinsmith.interval.sensor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.notice_sensor_failed
import dev.justinsmith.interval.history.resources.notice_sensor_no_access
import dev.justinsmith.interval.history.resources.notice_sensor_not_found
import dev.justinsmith.interval.history.resources.notice_sensor_nothing
import dev.justinsmith.interval.history.resources.sensor_ready_text
import dev.justinsmith.interval.history.resources.sensor_ready_title
import dev.justinsmith.interval.history.resources.sensor_record
import dev.justinsmith.interval.history.resources.sensor_recording_text
import dev.justinsmith.interval.history.resources.sensor_recording_title
import dev.justinsmith.interval.history.resources.sensor_sync
import dev.justinsmith.interval.history.resources.sensor_working
import dev.justinsmith.interval.platform.SensorFailure
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Spacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The heart-rate sensor on Today: with one paired on a class's day, the way to set it recording; with a class on
 * it, since when, and the way to sync it. Its button says it is reaching the sensor while it is ([busy]), which
 * takes some seconds, and takes no tap then.
 */
@Composable
fun ClassSensorCard(
    card: SensorCard,
    busy: Boolean,
    onRecord: () -> Unit,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    val working = stringResource(Res.string.sensor_working)
    ListCard(modifier) {
        Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            val (title, text) = when (card) {
                is SensorCard.Ready ->
                    stringResource(Res.string.sensor_ready_title) to
                        stringResource(Res.string.sensor_ready_text, card.name)

                is SensorCard.Recording ->
                    stringResource(Res.string.sensor_recording_title, card.since) to
                        stringResource(Res.string.sensor_recording_text)
            }
            Text(
                title,
                style = IntervalTheme.type.label,
                color = palette.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(text, style = IntervalTheme.type.meta, color = palette.textDim)
            when (card) {
                is SensorCard.Ready -> SecondaryButton(
                    if (busy) working else stringResource(Res.string.sensor_record),
                    onRecord,
                    Modifier.fillMaxWidth(),
                    enabled = !busy,
                )

                is SensorCard.Recording -> AccentButton(
                    if (busy) working else stringResource(Res.string.sensor_sync),
                    onSync,
                    Modifier.fillMaxWidth(),
                    enabled = !busy,
                )
            }
        }
    }
}

/** What a failure to reach the sensor is said as. */
val SensorFailure.words: StringResource
    get() = when (this) {
        SensorFailure.NoAccess -> Res.string.notice_sensor_no_access
        SensorFailure.NotFound -> Res.string.notice_sensor_not_found
        SensorFailure.Failed -> Res.string.notice_sensor_failed
        SensorFailure.NothingRecorded -> Res.string.notice_sensor_nothing
    }

@PreviewFormFactors
@Composable
private fun ClassSensorCardPreview() {
    IntervalTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            ClassSensorCard(SensorCard.Ready("Polar Sense 1A2B3C4D"), busy = false, onRecord = {}, onSync = {})
            ClassSensorCard(SensorCard.Recording("6:02 PM"), busy = false, onRecord = {}, onSync = {})
            ClassSensorCard(SensorCard.Recording("6:02 PM"), busy = true, onRecord = {}, onSync = {})
        }
    }
}
