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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
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
import dev.justinsmith.interval.resources.preset_editor_same_shape
import dev.justinsmith.interval.resources.preset_editor_saves_as
import dev.justinsmith.interval.resources.preset_editor_total
import dev.justinsmith.interval.resources.preset_name_count
import dev.justinsmith.interval.resources.preset_name_label
import dev.justinsmith.interval.resources.preset_save_title
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.defaultPresetName
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.sameShape
import dev.justinsmith.interval.timer.stepped
import dev.justinsmith.interval.timer.storedName
import dev.justinsmith.interval.timer.valueOf
import dev.justinsmith.interval.timer.withValue
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.FieldRow
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.ValueSheet
import org.jetbrains.compose.resources.stringResource

/**
 * What the editor opened to do, which names its title. [SaveCurrent] saves the settings on the Timer,
 * from the Timer, the Presets list or a workout's summary; [New] starts a preset from them without
 * touching the Timer.
 */
internal enum class EditorMode { Edit, New, Copy, SaveCurrent }

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
 * name saves as the shape's own ("40/20 ×6"), which the empty field shows. Before Save it says what
 * the repository will do with a name another of [presets] has ("Saves as Tabata 2") and which one
 * already has this shape. Each change to the shape goes back through [onChangeShape], so the [draft]
 * lives with its caller.
 */
@Composable
internal fun PresetEditor(
    draft: PresetDraft,
    prepSeconds: Int,
    presets: List<Preset>,
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
            EditorMode.SaveCurrent -> Res.string.preset_save_title
        },
    )
    // The one being edited is not "another" preset: keeping its own name or shape is no clash.
    val others = presets.filterNot { draft.id != null && it.id == draft.id }
    // What the user means, before another preset's name makes it numbered: shown when the two differ.
    val wanted = name.text.toString().trim().ifEmpty { placeholder }
    val storedName = Preset(wanted, draft.config, draft.id).storedName(presets)
    val sameShape = others.firstOrNull { it.config.sameShape(draft.config) }
    // Saved by name: an enum is not saveable on every platform.
    var editingName by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingName?.let(ConfigField::valueOf)
    IntervalSheet(title = title, onDismiss = onDismiss) { hide ->
        val save = {
            onSave(Preset(wanted, draft.config, draft.id))
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
            if (storedName != wanted) EditorHint(stringResource(Res.string.preset_editor_saves_as, storedName))
        }
        Column(Modifier.fillMaxWidth().clip(Corners.radius18).background(palette.surfaceVariant)) {
            EditorFields.forEachIndexed { index, field ->
                if (index > 0) RowDivider()
                FieldRow(field, draft.config.valueOf(field), onClick = { editingName = field.name })
            }
        }
        EditorTotal(total)
        sameShape?.let { EditorHint(stringResource(Res.string.preset_editor_same_shape, it.name)) }
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

/** A line under a field saying what Save will do differently from what the user might expect. */
@Composable
private fun EditorHint(text: String) {
    Text(
        text,
        style = IntervalTheme.type.meta,
        color = IntervalTheme.palette.textDim,
        modifier = Modifier.padding(horizontal = Spacing.space4),
    )
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

/**
 * The editor's draft, held by whichever screen opened it (Presets, the Timer, a workout's summary)
 * and saved by its values, so an open editor survives a configuration change and process death.
 */
@Composable
internal fun rememberPresetDraft(): MutableState<PresetDraft?> =
    rememberSaveable(stateSaver = PresetDraftSaver) { mutableStateOf(null) }

/** A preset saved by its values, as a dialog's open state is. */
internal val PresetSaver: Saver<Preset?, Any> = mapSaver(
    save = { preset ->
        if (preset == null) emptyMap() else mapOf(NAME to preset.name, ID to preset.id) + preset.config.saved()
    },
    restore = { Preset(it[NAME] as String, it.restoredConfig(), it[ID] as Long?) },
)

private val PresetDraftSaver: Saver<PresetDraft?, Any> = mapSaver(
    save = { draft ->
        if (draft == null) {
            emptyMap()
        } else {
            mapOf(MODE to draft.mode.name, NAME to draft.name, ID to draft.id) + draft.config.saved()
        }
    },
    restore = {
        PresetDraft(EditorMode.valueOf(it[MODE] as String), it[NAME] as String, it.restoredConfig(), it[ID] as Long?)
    },
)

private fun IntervalConfig.saved(): Map<String, Any?> =
    mapOf(PREP to prepSeconds, WORK to workSeconds, REST to restSeconds, ROUNDS to rounds)

private fun Map<String, Any?>.restoredConfig(): IntervalConfig =
    IntervalConfig(this[PREP] as Int, this[WORK] as Int, this[REST] as Int, this[ROUNDS] as Int)

private const val MODE = "mode"
private const val NAME = "name"
private const val ID = "id"
private const val PREP = "prep"
private const val WORK = "work"
private const val REST = "rest"
private const val ROUNDS = "rounds"
