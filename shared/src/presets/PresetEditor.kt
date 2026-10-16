package dev.justinsmith.interval.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.common_cancel
import dev.justinsmith.interval.resources.common_save
import dev.justinsmith.interval.resources.preset_editor_copy_title
import dev.justinsmith.interval.resources.preset_editor_edit_title
import dev.justinsmith.interval.resources.preset_editor_new_title
import dev.justinsmith.interval.resources.preset_editor_prep_hint
import dev.justinsmith.interval.resources.preset_editor_total
import dev.justinsmith.interval.resources.preset_name_count
import dev.justinsmith.interval.resources.preset_name_label
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.defaultPresetName
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.stepped
import dev.justinsmith.interval.timer.valueOf
import dev.justinsmith.interval.timer.withValue
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.FieldRow
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.NameField
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.ValueSheet
import org.jetbrains.compose.resources.stringResource

/** What the editor opened to do, which names its title. */
internal enum class EditorMode { Edit, New, Copy }

/**
 * A preset as the editor has it: the shape being set, the name it opened with (the field holds what
 * is typed after that) and, for one of the user's own being edited, its [id].
 */
internal data class PresetDraft(
    val mode: EditorMode,
    val name: String,
    val config: IntervalConfig,
    val id: Long? = null,
)

/** The values the editor sets. Get ready is a setting, edited from Settings. */
private val EditorFields = listOf(ConfigField.Work, ConfigField.Rest, ConfigField.Rounds)

/**
 * Names and shapes a preset in a sheet: the name with its count, Work, Rest and Rounds as rows that
 * open the edit sheet, the total it comes to with the user's get-ready countdown, and Save. A blank
 * name saves as the shape's own ("40/20 ×6"), which the empty field shows. Each change to the shape
 * goes back through [onChangeShape], so the [draft] lives with its caller.
 */
@Composable
internal fun PresetEditor(
    draft: PresetDraft,
    prepSeconds: Int,
    onChangeShape: ((IntervalConfig) -> IntervalConfig) -> Unit,
    onSave: (Preset) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = IntervalTheme.palette
    val name = rememberTextFieldState(draft.name)
    val placeholder = draft.config.defaultPresetName()
    val total = formatClock(draft.config.copy(prepSeconds = prepSeconds).totalSeconds)
    val title = stringResource(
        when (draft.mode) {
            EditorMode.Edit -> Res.string.preset_editor_edit_title
            EditorMode.New -> Res.string.preset_editor_new_title
            EditorMode.Copy -> Res.string.preset_editor_copy_title
        },
    )
    // Saved by name: an enum is not saveable on every platform.
    var editingName by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingName?.let(ConfigField::valueOf)
    IntervalSheet(title = title, onDismiss = onDismiss) { hide ->
        val save = {
            onSave(Preset(name.text.toString().trim().ifEmpty { placeholder }, draft.config, draft.id))
            hide()
        }
        EditorHeader(title, onCancel = hide)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.space4), verticalAlignment = Alignment.Bottom) {
                Text(
                    stringResource(Res.string.preset_name_label),
                    style = IntervalTheme.type.caption,
                    color = palette.textDim,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(Res.string.preset_name_count, name.text.length, Preset.MAX_NAME_LENGTH),
                    style = IntervalTheme.type.caption,
                    color = palette.textDim,
                )
            }
            NameField(name, placeholder = placeholder, onDone = save)
        }
        Column(Modifier.fillMaxWidth().clip(Corners.radius18).background(palette.surfaceVariant)) {
            EditorFields.forEachIndexed { index, field ->
                if (index > 0) RowDivider()
                FieldRow(field, draft.config.valueOf(field), onClick = { editingName = field.name })
            }
        }
        EditorTotal(total)
        AccentButton(stringResource(Res.string.common_save), onClick = save, modifier = Modifier.fillMaxWidth())
    }
    editing?.let { field ->
        ValueSheet(
            field = field,
            value = draft.config.valueOf(field),
            total = total,
            onStep = { direction -> onChangeShape { it.stepped(field, direction) } },
            onPick = { value -> onChangeShape { it.withValue(field, value) } },
            onDismiss = { editingName = null },
        )
    }
}

/** Cancel at the start and the [title] centred, as a sheet's header. */
@Composable
private fun EditorHeader(title: String, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        QuietTextButton(
            stringResource(Res.string.common_cancel),
            onClick = onCancel,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            title,
            style = IntervalTheme.type.value,
            color = IntervalTheme.palette.text,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** The whole workout's length with the user's get-ready countdown, and where that countdown is set. */
@Composable
private fun EditorTotal(total: String, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(modifier.padding(horizontal = Spacing.space4), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(Res.string.preset_editor_total),
                style = IntervalTheme.type.body,
                color = palette.textDim,
                modifier = Modifier.weight(1f),
            )
            Text(total, style = IntervalTheme.type.section, color = palette.text)
        }
        Text(
            stringResource(Res.string.preset_editor_prep_hint),
            style = IntervalTheme.type.meta,
            color = palette.textDim,
        )
    }
}
