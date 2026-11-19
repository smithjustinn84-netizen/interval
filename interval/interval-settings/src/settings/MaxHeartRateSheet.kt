package dev.justinsmith.interval.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.MAX_BPM_RANGE
import dev.justinsmith.interval.settings.resources.Res
import dev.justinsmith.interval.settings.resources.common_done
import dev.justinsmith.interval.settings.resources.settings_max_hr
import dev.justinsmith.interval.settings.resources.settings_max_hr_less_cd
import dev.justinsmith.interval.settings.resources.settings_max_hr_more_cd
import dev.justinsmith.interval.settings.resources.settings_max_hr_reset
import dev.justinsmith.interval.settings.resources.value_sheet_hint_hold
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
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
