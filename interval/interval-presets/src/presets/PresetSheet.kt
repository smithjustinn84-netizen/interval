package dev.justinsmith.interval.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.presets.resources.Res
import dev.justinsmith.interval.presets.resources.common_calories_estimate
import dev.justinsmith.interval.presets.resources.common_prep_line
import dev.justinsmith.interval.presets.resources.preset_copy_name
import dev.justinsmith.interval.presets.resources.presets_built_in
import dev.justinsmith.interval.presets.resources.presets_calories_estimate_as
import dev.justinsmith.interval.presets.resources.presets_delete
import dev.justinsmith.interval.presets.resources.presets_edit
import dev.justinsmith.interval.presets.resources.presets_put_on_timer
import dev.justinsmith.interval.presets.resources.presets_replaces_unsaved
import dev.justinsmith.interval.presets.resources.presets_save_copy
import dev.justinsmith.interval.presets.resources.presets_yours
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StartButton
import dev.justinsmith.interval.ui.Timeline
import dev.justinsmith.interval.ui.labelRes
import dev.justinsmith.interval.ui.workoutDescription
import org.jetbrains.compose.resources.stringResource

/**
 * What one preset leads to, in a sheet over the list: its group and name, its total with the user's
 * get-ready countdown over a full-width timeline, its shape and the calories it is estimated to burn, then
 * Start workout, the one filled button, over a row of quiet ones: Put on Timer (left out for the preset
 * already there), then Edit and Delete for one of the user's own, or Save a copy for a built-in, which opens
 * the editor on a copy named after it. When [replacesUnsaved], the Timer holds settings no preset has, and
 * a line over Start says that it or Put on Timer replaces them.
 */
@Composable
internal fun PresetSheet(
    row: PresetRowItem,
    prepSeconds: Int,
    replacesUnsaved: Boolean,
    onStart: () -> Unit,
    onPutOnTimer: () -> Unit,
    onEdit: (PresetDraft) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = IntervalTheme.palette
    val preset = row.preset
    val copyName = stringResource(Res.string.preset_copy_name, preset.name).take(Preset.MAX_NAME_LENGTH)
    IntervalSheet(title = preset.name, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            Text(
                stringResource(if (preset.custom) Res.string.presets_yours else Res.string.presets_built_in),
                style = IntervalTheme.type.caption,
                color = palette.textDim,
            )
            Text(preset.name, style = IntervalTheme.type.value, color = palette.text)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space12)) {
                Text(
                    row.total,
                    style = IntervalTheme.type.display,
                    color = palette.text,
                    modifier = Modifier.alignByBaseline(),
                )
                if (prepSeconds > 0) {
                    Text(
                        stringResource(Res.string.common_prep_line, formatClock(prepSeconds)),
                        style = IntervalTheme.type.meta,
                        color = palette.textDim,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
            Timeline(preset.config, Modifier.fillMaxWidth(), showPrep = false)
            Text(workoutDescription(preset.config), style = IntervalTheme.type.body, color = palette.textDim)
            estimateText(row)?.let { Text(it, style = IntervalTheme.type.meta, color = palette.textDim) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            if (replacesUnsaved) {
                Text(
                    stringResource(Res.string.presets_replaces_unsaved),
                    style = IntervalTheme.type.meta,
                    color = palette.textDim,
                )
            }
            StartButton(onStart, Modifier.fillMaxWidth())
        }
        // Start is the one filled button, as on a flow's sheet; the rest are quiet, in one row.
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            if (!row.selected) {
                QuietTextButton(stringResource(Res.string.presets_put_on_timer), onPutOnTimer, Modifier.weight(1f))
            }
            if (preset.custom) {
                QuietTextButton(
                    stringResource(Res.string.presets_edit),
                    onClick = {
                        onEdit(PresetDraft(EditorMode.Edit, preset.name, preset.config, preset.id, preset.alternateHands))
                    },
                    modifier = Modifier.weight(1f),
                )
                QuietTextButton(
                    stringResource(Res.string.presets_delete),
                    onDelete,
                    Modifier.weight(1f),
                    destructive = true,
                )
            } else {
                QuietTextButton(
                    stringResource(Res.string.presets_save_copy),
                    onClick = {
                        onEdit(PresetDraft(EditorMode.Copy, copyName, preset.config, alternateHands = preset.alternateHands))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * "≈ 32 kcal", or "≈ 32 kcal as Bike" when the estimate borrows the Timer's activity because the preset names
 * none; null without a body weight to estimate from.
 */
@Composable
private fun estimateText(row: PresetRowItem): String? {
    val calories = row.calories ?: return null
    val estimatedAs = row.estimatedAs ?: return stringResource(Res.string.common_calories_estimate, calories)
    return stringResource(Res.string.presets_calories_estimate_as, calories, stringResource(estimatedAs.labelRes))
}
