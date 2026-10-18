package dev.justinsmith.interval.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.common_prep_line
import dev.justinsmith.interval.resources.preset_copy_name
import dev.justinsmith.interval.resources.presets_built_in
import dev.justinsmith.interval.resources.presets_delete
import dev.justinsmith.interval.resources.presets_edit
import dev.justinsmith.interval.resources.presets_put_on_timer
import dev.justinsmith.interval.resources.presets_replaces_unsaved
import dev.justinsmith.interval.resources.presets_save_copy
import dev.justinsmith.interval.resources.presets_yours
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StartButton
import dev.justinsmith.interval.ui.Timeline
import dev.justinsmith.interval.ui.workoutDescription
import org.jetbrains.compose.resources.stringResource

/**
 * What one preset leads to, in a sheet over the list: its group and name, its total with the user's
 * get-ready countdown over a full-width timeline and its shape, then Start workout and Put on Timer.
 * Put on Timer is left out for the preset already there. When [replacesUnsaved], the Timer holds
 * settings no preset has, and a line over the buttons says either one replaces them. One of the
 * user's own ends with Edit and Delete; a built-in with Save a copy, which opens the editor on a copy
 * named after it.
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
            if (!row.selected) {
                SecondaryButton(stringResource(Res.string.presets_put_on_timer), onPutOnTimer, Modifier.fillMaxWidth())
            }
        }
        if (preset.custom) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                QuietTextButton(
                    stringResource(Res.string.presets_edit),
                    onClick = { onEdit(PresetDraft(EditorMode.Edit, preset.name, preset.config, preset.id)) },
                    modifier = Modifier.weight(1f),
                )
                QuietTextButton(stringResource(Res.string.presets_delete), onDelete, Modifier.weight(1f))
            }
        } else {
            QuietTextButton(
                stringResource(Res.string.presets_save_copy),
                onClick = { onEdit(PresetDraft(EditorMode.Copy, copyName, preset.config)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
