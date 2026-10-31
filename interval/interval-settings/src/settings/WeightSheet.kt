package dev.justinsmith.interval.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.interval.settings.resources.Res
import dev.justinsmith.interval.settings.resources.common_done
import dev.justinsmith.interval.settings.resources.settings_weight
import dev.justinsmith.interval.settings.resources.unit_kg
import dev.justinsmith.interval.settings.resources.unit_lb
import dev.justinsmith.interval.settings.resources.value_sheet_hint_hold
import dev.justinsmith.interval.settings.resources.weight_less_cd
import dev.justinsmith.interval.settings.resources.weight_more_cd
import dev.justinsmith.interval.settings.resources.weight_sheet_clear
import dev.justinsmith.interval.settings.resources.weight_sheet_privacy
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.SegmentedPicker
import dev.justinsmith.interval.ui.SheetStepper
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The body weight's edit sheet: kg or lb, then − / + steppers (hold to repeat). Changes apply as they are
 * made. With calorie estimates off ([weight] null) it shows the unit's default, which a step or Done sets;
 * closing the sheet any other way leaves them off. [onClear] forgets the weight, which turns the estimates
 * off. It is offered only when the sheet opened on a weight, so the first step never moves the buttons
 * under the user's finger.
 */
@Composable
internal fun WeightSheet(
    weight: Int?,
    unit: WeightUnit,
    onSetWeight: (Int) -> Unit,
    onSetUnit: (WeightUnit) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = IntervalTheme.palette
    val shown = weight ?: unit.default
    val canClear = rememberSaveable { weight != null }
    IntervalSheet(title = stringResource(Res.string.settings_weight), onDismiss = onDismiss) { hide ->
        Text(stringResource(Res.string.settings_weight), style = IntervalTheme.type.value, color = palette.text)
        SegmentedPicker(WeightUnit.entries, unit, label = { stringResource(it.labelRes) }, onSelect = onSetUnit)
        SheetStepper(
            shown,
            shown.toString(),
            stringResource(Res.string.weight_less_cd),
            stringResource(Res.string.weight_more_cd),
            canLower = shown > unit.range.first,
            canRaise = shown < unit.range.last,
            onStep = { onSetWeight(shown + it) },
            hint = stringResource(Res.string.value_sheet_hint_hold),
            label = stringResource(unit.labelRes),
        )
        Text(
            stringResource(Res.string.weight_sheet_privacy),
            style = IntervalTheme.type.meta,
            color = palette.textDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        AccentButton(
            stringResource(Res.string.common_done),
            onClick = {
                if (weight == null) onSetWeight(shown)
                hide()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (canClear) {
            QuietTextButton(
                stringResource(Res.string.weight_sheet_clear),
                onClick = {
                    onClear()
                    hide()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** How a weight's unit is written: "kg", "lb". [WeightUnit.name] is what is stored. */
internal val WeightUnit.labelRes: StringResource
    get() = when (this) {
        WeightUnit.Kg -> Res.string.unit_kg
        WeightUnit.Lb -> Res.string.unit_lb
    }
