package dev.justinsmith.interval.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.format
import dev.justinsmith.interval.ui.resources.Res
import dev.justinsmith.interval.ui.resources.config_rounds_short
import dev.justinsmith.interval.ui.resources.field_cool_down_edit
import dev.justinsmith.interval.ui.resources.field_cool_down_hint
import dev.justinsmith.interval.ui.resources.field_cool_down_label
import dev.justinsmith.interval.ui.resources.field_cool_down_less
import dev.justinsmith.interval.ui.resources.field_cool_down_more
import dev.justinsmith.interval.ui.resources.field_off
import dev.justinsmith.interval.ui.resources.field_prep_edit
import dev.justinsmith.interval.ui.resources.field_prep_hint
import dev.justinsmith.interval.ui.resources.field_prep_label
import dev.justinsmith.interval.ui.resources.field_prep_less
import dev.justinsmith.interval.ui.resources.field_prep_more
import dev.justinsmith.interval.ui.resources.field_rest_edit
import dev.justinsmith.interval.ui.resources.field_rest_hint
import dev.justinsmith.interval.ui.resources.field_rest_label
import dev.justinsmith.interval.ui.resources.field_rest_less
import dev.justinsmith.interval.ui.resources.field_rest_more
import dev.justinsmith.interval.ui.resources.field_rounds_edit
import dev.justinsmith.interval.ui.resources.field_rounds_hint
import dev.justinsmith.interval.ui.resources.field_rounds_label
import dev.justinsmith.interval.ui.resources.field_rounds_less
import dev.justinsmith.interval.ui.resources.field_rounds_more
import dev.justinsmith.interval.ui.resources.field_warm_up_edit
import dev.justinsmith.interval.ui.resources.field_warm_up_hint
import dev.justinsmith.interval.ui.resources.field_warm_up_label
import dev.justinsmith.interval.ui.resources.field_warm_up_less
import dev.justinsmith.interval.ui.resources.field_warm_up_more
import dev.justinsmith.interval.ui.resources.field_work_edit
import dev.justinsmith.interval.ui.resources.field_work_hint
import dev.justinsmith.interval.ui.resources.field_work_label
import dev.justinsmith.interval.ui.resources.field_work_less
import dev.justinsmith.interval.ui.resources.field_work_more
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** How each [ConfigField] is named wherever it is edited. */
val ConfigField.labelRes: StringResource
    get() = when (this) {
        ConfigField.Prep -> Res.string.field_prep_label
        ConfigField.Work -> Res.string.field_work_label
        ConfigField.Rest -> Res.string.field_rest_label
        ConfigField.Rounds -> Res.string.field_rounds_label
        ConfigField.WarmUp -> Res.string.field_warm_up_label
        ConfigField.CoolDown -> Res.string.field_cool_down_label
    }

/** The line under the name, saying what the value is for. */
val ConfigField.hintRes: StringResource
    get() = when (this) {
        ConfigField.Prep -> Res.string.field_prep_hint
        ConfigField.Work -> Res.string.field_work_hint
        ConfigField.Rest -> Res.string.field_rest_hint
        ConfigField.Rounds -> Res.string.field_rounds_hint
        ConfigField.WarmUp -> Res.string.field_warm_up_hint
        ConfigField.CoolDown -> Res.string.field_cool_down_hint
    }

/** What tapping the value's row does, for screen readers: "Edit work". */
val ConfigField.editRes: StringResource
    get() = when (this) {
        ConfigField.Prep -> Res.string.field_prep_edit
        ConfigField.Work -> Res.string.field_work_edit
        ConfigField.Rest -> Res.string.field_rest_edit
        ConfigField.Rounds -> Res.string.field_rounds_edit
        ConfigField.WarmUp -> Res.string.field_warm_up_edit
        ConfigField.CoolDown -> Res.string.field_cool_down_edit
    }

/** The − stepper, for screen readers: "Less work time", "Fewer rounds". */
val ConfigField.lessRes: StringResource
    get() = when (this) {
        ConfigField.Prep -> Res.string.field_prep_less
        ConfigField.Work -> Res.string.field_work_less
        ConfigField.Rest -> Res.string.field_rest_less
        ConfigField.Rounds -> Res.string.field_rounds_less
        ConfigField.WarmUp -> Res.string.field_warm_up_less
        ConfigField.CoolDown -> Res.string.field_cool_down_less
    }

/** The + stepper, for screen readers: "More work time", "More rounds". */
val ConfigField.moreRes: StringResource
    get() = when (this) {
        ConfigField.Prep -> Res.string.field_prep_more
        ConfigField.Work -> Res.string.field_work_more
        ConfigField.Rest -> Res.string.field_rest_more
        ConfigField.Rounds -> Res.string.field_rounds_more
        ConfigField.WarmUp -> Res.string.field_warm_up_more
        ConfigField.CoolDown -> Res.string.field_cool_down_more
    }

/** The phase stripe that ties a field to its workout colour; Rounds takes the accent. */
@Composable
@ReadOnlyComposable
fun ConfigField.stripe(): Color = when (this) {
    ConfigField.Prep -> IntervalTheme.palette.prepMark

    ConfigField.Work -> IntervalTheme.palette.workMark

    ConfigField.Rest -> IntervalTheme.palette.restMark

    ConfigField.Rounds -> IntervalTheme.palette.accent

    // Easier stretches either side of the rounds: the warm-up in get ready's colour, the cool-down in rest's.
    ConfigField.WarmUp -> IntervalTheme.palette.prepMark

    ConfigField.CoolDown -> IntervalTheme.palette.restMark
}

/** The pale wash of the field's colour behind its value. */
@Composable
@ReadOnlyComposable
fun ConfigField.tint(): Color = IntervalTheme.palette.tint(stripe())

/** The field's value on its [tint]: its own colour, except get ready's amber, darkened to read. */
@Composable
@ReadOnlyComposable
fun ConfigField.valueColor(): Color =
    if (this == ConfigField.Prep || this == ConfigField.WarmUp) IntervalTheme.palette.onPrepTint else stripe()

/** A warm-up or cool-down of no length is none: "Off". */
fun ConfigField.isOff(value: Int): Boolean = this in ConfigField.bookends && value == 0

/** [value] as a sheet or picker shows it: a clock, a count, or "Off" for a warm-up or cool-down of none. */
@Composable
fun ConfigField.valueText(
    value: Int,
): String = if (isOff(value)) stringResource(Res.string.field_off) else format(value)

/** [value] as a field's card shows it: rounds as "×8", times as a clock, a bookend of none as "Off". */
@Composable
fun ConfigField.displayText(value: Int): String =
    if (this == ConfigField.Rounds) stringResource(Res.string.config_rounds_short, value) else valueText(value)

/**
 * The − ([more] false) or + of a field's stepper: it steps [onStep] by one and is disabled at that end
 * of the field's range.
 */
@Composable
fun FieldStepButton(
    field: ConfigField,
    value: Int,
    more: Boolean,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    RepeatButton(
        if (more) "+" else "−",
        stringResource(if (more) field.moreRes else field.lessRes),
        enabled = if (more) value < field.range.last else value > field.range.first,
        onStep = { onStep(if (more) 1 else -1) },
        modifier = modifier,
    )
}
