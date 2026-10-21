package dev.justinsmith.interval.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.em
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.activity_choose_cd
import dev.justinsmith.interval.resources.common_calories_estimate
import dev.justinsmith.interval.resources.common_joined
import dev.justinsmith.interval.resources.common_prep_line
import dev.justinsmith.interval.resources.common_settings
import dev.justinsmith.interval.resources.timer_all_presets_cd
import dev.justinsmith.interval.resources.timer_describe_cd
import dev.justinsmith.interval.resources.timer_save_as_preset_cd
import dev.justinsmith.interval.resources.timer_title
import dev.justinsmith.interval.resources.timer_total
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.valueOf
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.FieldStepButton
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.RollingText
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StartButton
import dev.justinsmith.interval.ui.TitleBar
import dev.justinsmith.interval.ui.displayText
import dev.justinsmith.interval.ui.editRes
import dev.justinsmith.interval.ui.labelRes
import dev.justinsmith.interval.ui.phaseStripe
import dev.justinsmith.interval.ui.presetNameOrCustom
import dev.justinsmith.interval.ui.rememberRising
import dev.justinsmith.interval.ui.stripe
import dev.justinsmith.interval.ui.surfaceButton
import org.jetbrains.compose.resources.stringResource

/**
 * The title, with + to save a shape no preset has yet, the way to describe a workout in words, and the
 * settings gear. The + grows in as the
 * shape leaves the presets behind and shrinks away as it lands on one, the gear sliding over to make
 * room, so the bar never jumps.
 */
@Composable
internal fun TimerTitleBar(
    canSavePreset: Boolean,
    onSave: () -> Unit,
    onDescribe: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    TitleBar(stringResource(Res.string.timer_title)) {
        AnimatedVisibility(
            canSavePreset,
            enter = fadeIn(tween(Motion.STATE_MILLIS)) + scaleIn(tween(Motion.STATE_MILLIS)) +
                expandHorizontally(tween(Motion.STATE_MILLIS)),
            exit = fadeOut(tween(Motion.STATE_MILLIS)) + scaleOut(tween(Motion.STATE_MILLIS)) +
                shrinkHorizontally(tween(Motion.STATE_MILLIS)),
        ) {
            IconAction(IntervalIcons.Add, stringResource(Res.string.timer_save_as_preset_cd), onSave)
        }
        IconAction(IntervalIcons.Describe, stringResource(Res.string.timer_describe_cd), onDescribe)
        IconAction(IntervalIcons.Settings, stringResource(Res.string.common_settings), onOpenSettings)
    }
}

/**
 * "Tabata · Total" over the session's length, the get-ready countdown when there is one, and the
 * calories it should burn once a body weight is set. The length and the calories roll as the steppers
 * change them, up when they grow and down when they shrink.
 */
@Composable
internal fun Total(state: TimerUiState, onEditActivity: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val seconds = state.config.totalSeconds
    val rising = rememberRising(seconds)
    // The line keeps its last countdown while it shrinks away, rather than reading "0:00" as it goes.
    val prep = state.config.prepSeconds
    var lastPrep by remember { mutableIntStateOf(prep) }
    SideEffect { if (prep > 0) lastPrep = prep }
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(
                Res.string.common_joined,
                presetNameOrCustom(state.presetName),
                stringResource(Res.string.timer_total),
            ),
            style = IntervalTheme.type.caption,
            color = palette.textDim,
        )
        RollingText(state.total, rising, style = IntervalTheme.type.display, color = palette.text)
        AnimatedVisibility(
            prep > 0,
            enter = fadeIn(tween(Motion.STATE_MILLIS)) + expandVertically(tween(Motion.STATE_MILLIS)),
            exit = fadeOut(tween(Motion.STATE_MILLIS)) + shrinkVertically(tween(Motion.STATE_MILLIS)),
        ) {
            Text(
                stringResource(Res.string.common_prep_line, formatClock(if (prep > 0) prep else lastPrep)),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
        }
        Row(
            Modifier.padding(top = Spacing.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
        ) {
            ActivityPill(state.config.activity, onEditActivity)
            state.calories?.let { calories ->
                RollingText(
                    stringResource(Res.string.common_calories_estimate, calories),
                    rising,
                    style = IntervalTheme.type.meta,
                    color = palette.textDim,
                )
            }
        }
    }
}

/**
 * What the work intervals are spent doing, as a small pill with a chevron that opens the picker. Shown
 * with or without a body weight, so the activity can be set before calories are turned on.
 */
@Composable
private fun ActivityPill(activity: Activity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(activity.labelRes)
    val description = stringResource(Res.string.activity_choose_cd, label)
    Row(
        modifier
            .heightIn(min = Sizes.pillHeight)
            .surfaceButton(interaction, Corners.full, palette.surfaceVariant, onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(start = Spacing.space16, end = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        Text(label, style = IntervalTheme.type.label, color = palette.text)
        Icon(
            IntervalIcons.Chevron,
            contentDescription = null,
            tint = palette.textDim,
            modifier = Modifier.size(Sizes.icon),
        )
    }
}

/** Work, Rest and Rounds, sharing the height they're given. */
@Composable
internal fun ColumnScope.StepperRows(
    config: IntervalConfig,
    onStep: (ConfigField, Int) -> Unit,
    onEdit: (ConfigField) -> Unit,
) {
    ConfigField.shape.forEachIndexed { index, field ->
        if (index > 0) Spacer(Modifier.size(Spacing.space8))
        StepperRow(
            field,
            config.valueOf(field),
            onStep = { onStep(field, it) },
            onEdit = { onEdit(field) },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * One value as a card: its phase stripe down the start edge, its name in the stripe's colour over the
 * value as big as the row allows, and − and + at thumb reach. The name and value open the edit sheet.
 */
@Composable
private fun StepperRow(
    field: ConfigField,
    value: Int,
    onStep: (Int) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    val stripe = field.stripe()
    val text = field.displayText(value)
    // The whole card opens the sheet, so its ripple and press fill the card; − and + take their own taps.
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.intervalRow)
            .surfaceButton(
                interaction,
                Corners.radius18,
                palette.surface,
                onClick = onEdit,
                onClickLabel = stringResource(field.editRes),
            )
            .phaseStripe(stripe)
            .padding(start = Sizes.stripe + Spacing.space16, end = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(vertical = Spacing.space8),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(field.labelRes), style = IntervalTheme.type.label, color = stripe)
            BasicText(
                text,
                // A line height in em, so it shrinks with the auto-sized font; display's 96.sp would not.
                style = IntervalTheme.type.display.copy(color = palette.text, lineHeight = 1.em),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = IntervalTheme.type.stat.fontSize,
                    maxFontSize = Sizes.stepperValueMax,
                ),
            )
        }
        FieldStepButton(field, value, more = false, onStep = onStep)
        FieldStepButton(field, value, more = true, onStep = onStep)
    }
}

/** Start workout, and beside it the way to every preset. */
@Composable
internal fun StartRow(onStart: () -> Unit, onSeeAllPresets: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val description = stringResource(Res.string.timer_all_presets_cd)
    val interaction = remember { MutableInteractionSource() }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        StartButton(onStart, Modifier.weight(1f))
        Box(
            Modifier
                .size(Sizes.start)
                .surfaceButton(interaction, Corners.radius18, palette.surface, onSeeAllPresets)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                IntervalIcons.Presets,
                contentDescription = null,
                tint = palette.text,
                modifier = Modifier.size(Sizes.icon),
            )
        }
    }
}
