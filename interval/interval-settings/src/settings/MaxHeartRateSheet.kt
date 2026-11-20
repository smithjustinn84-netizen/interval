package dev.justinsmith.interval.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.MAX_BPM_RANGE
import dev.justinsmith.interval.platform.RESTING_BPM_RANGE
import dev.justinsmith.interval.settings.resources.Res
import dev.justinsmith.interval.settings.resources.common_done
import dev.justinsmith.interval.settings.resources.settings_max_hr
import dev.justinsmith.interval.settings.resources.settings_max_hr_less_cd
import dev.justinsmith.interval.settings.resources.settings_max_hr_more_cd
import dev.justinsmith.interval.settings.resources.settings_max_hr_reset
import dev.justinsmith.interval.settings.resources.settings_resting_hr
import dev.justinsmith.interval.settings.resources.settings_resting_hr_how
import dev.justinsmith.interval.settings.resources.settings_resting_hr_less_cd
import dev.justinsmith.interval.settings.resources.settings_resting_hr_more_cd
import dev.justinsmith.interval.settings.resources.settings_resting_hr_reset
import dev.justinsmith.interval.settings.resources.value_sheet_hint_hold
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.SheetStepper
import org.jetbrains.compose.resources.stringResource

/**
 * Edit sheet for the user's maximum heart rate (used to compute the five training zones).
 * Defaults to [DEFAULT_MAX_BPM] when unset; [onSetMaxHeartRate] with null restores the default.
 */
@Composable
internal fun MaxHeartRateSheet(
    maxHeartRate: Int?,
    onSetMaxHeartRate: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = IntervalTheme.palette
    val shown = maxHeartRate ?: DEFAULT_MAX_BPM
    val canReset = rememberSaveable { maxHeartRate != null }
    IntervalSheet(title = stringResource(Res.string.settings_max_hr), onDismiss = onDismiss) { hide ->
        Text(stringResource(Res.string.settings_max_hr), style = IntervalTheme.type.value, color = palette.text)
        SheetStepper(
            shown,
            shown.toString(),
            stringResource(Res.string.settings_max_hr_less_cd),
            stringResource(Res.string.settings_max_hr_more_cd),
            canLower = shown > MAX_BPM_RANGE.first,
            canRaise = shown < MAX_BPM_RANGE.last,
            onStep = { onSetMaxHeartRate((shown + it).coerceIn(MAX_BPM_RANGE)) },
            hint = stringResource(Res.string.value_sheet_hint_hold),
        )
        AccentButton(
            stringResource(Res.string.common_done),
            onClick = {
                if (maxHeartRate == null) onSetMaxHeartRate(shown)
                hide()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (canReset) {
            QuietTextButton(
                stringResource(Res.string.settings_max_hr_reset),
                onClick = {
                    onSetMaxHeartRate(null)
                    hide()
                },
            )
        }
    }
}

/** Where the stepper opens for a resting heart rate not set yet: a trained adult's, near enough. */
private const val RESTING_START_BPM = 60

/**
 * Edit sheet for the heart rate at rest, which with the max gives the reserve the zones are cut from; it says
 * how to take one. Unset until Done; [onSetRestingHeartRate] with null leaves it unset again.
 */
@Composable
internal fun RestingHeartRateSheet(
    restingHeartRate: Int?,
    onSetRestingHeartRate: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = IntervalTheme.palette
    val shown = restingHeartRate ?: RESTING_START_BPM
    val canReset = rememberSaveable { restingHeartRate != null }
    IntervalSheet(title = stringResource(Res.string.settings_resting_hr), onDismiss = onDismiss) { hide ->
        Text(stringResource(Res.string.settings_resting_hr), style = IntervalTheme.type.value, color = palette.text)
        Text(
            stringResource(Res.string.settings_resting_hr_how),
            style = IntervalTheme.type.meta,
            color = palette.textDim,
        )
        SheetStepper(
            shown,
            shown.toString(),
            stringResource(Res.string.settings_resting_hr_less_cd),
            stringResource(Res.string.settings_resting_hr_more_cd),
            canLower = shown > RESTING_BPM_RANGE.first,
            canRaise = shown < RESTING_BPM_RANGE.last,
            onStep = { onSetRestingHeartRate((shown + it).coerceIn(RESTING_BPM_RANGE)) },
            hint = stringResource(Res.string.value_sheet_hint_hold),
        )
        AccentButton(
            stringResource(Res.string.common_done),
            onClick = {
                if (restingHeartRate == null) onSetRestingHeartRate(shown)
                hide()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (canReset) {
            QuietTextButton(
                stringResource(Res.string.settings_resting_hr_reset),
                onClick = {
                    onSetRestingHeartRate(null)
                    hide()
                },
            )
        }
    }
}

@PreviewFormFactors
@Composable
private fun RestingHeartRateSheetPreview() {
    IntervalTheme {
        // A page under the sheet, as there always is: a sheet alone leaves nothing of the window to draw.
        Box(Modifier.fillMaxSize().background(IntervalTheme.palette.background))
        RestingHeartRateSheet(restingHeartRate = 52, onSetRestingHeartRate = {}, onDismiss = {})
    }
}
