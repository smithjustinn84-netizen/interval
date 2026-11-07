package dev.justinsmith.interval.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.interval.presets.resources.Res
import dev.justinsmith.interval.presets.resources.activity_title
import dev.justinsmith.interval.presets.resources.common_cancel
import dev.justinsmith.interval.presets.resources.common_done
import dev.justinsmith.interval.presets.resources.common_save
import dev.justinsmith.interval.presets.resources.notice_preset_saved
import dev.justinsmith.interval.presets.resources.preset_editor_add_block
import dev.justinsmith.interval.presets.resources.preset_editor_block
import dev.justinsmith.interval.presets.resources.preset_editor_copy_title
import dev.justinsmith.interval.presets.resources.preset_editor_edit_title
import dev.justinsmith.interval.presets.resources.preset_editor_move_down
import dev.justinsmith.interval.presets.resources.preset_editor_move_up
import dev.justinsmith.interval.presets.resources.preset_editor_new_title
import dev.justinsmith.interval.presets.resources.preset_editor_prep_hint
import dev.justinsmith.interval.presets.resources.preset_editor_remove_block
import dev.justinsmith.interval.presets.resources.preset_editor_same_shape
import dev.justinsmith.interval.presets.resources.preset_editor_save_as_preset
import dev.justinsmith.interval.presets.resources.preset_editor_saves_as
import dev.justinsmith.interval.presets.resources.preset_editor_switch_hands
import dev.justinsmith.interval.presets.resources.preset_editor_switch_hands_text
import dev.justinsmith.interval.presets.resources.preset_editor_timer_title
import dev.justinsmith.interval.presets.resources.preset_editor_total
import dev.justinsmith.interval.presets.resources.preset_name_count
import dev.justinsmith.interval.presets.resources.preset_name_label
import dev.justinsmith.interval.presets.resources.preset_save_title
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.Block
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.defaultPresetName
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.sameShape
import dev.justinsmith.interval.timer.stepped
import dev.justinsmith.interval.timer.storedName
import dev.justinsmith.interval.timer.valueOf
import dev.justinsmith.interval.timer.withBlockAdded
import dev.justinsmith.interval.timer.withBlockMoved
import dev.justinsmith.interval.timer.withValue
import dev.justinsmith.interval.timer.withoutBlock
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.ActivitySheet
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.FieldRow
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LinkRow
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.NameField
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.ToggleRow
import dev.justinsmith.interval.ui.ValueSheet
import dev.justinsmith.interval.ui.labelRes
import org.jetbrains.compose.resources.stringResource

/**
 * What the editor opened to do, which names its title. [SaveCurrent] saves the settings on the Timer,
 * from the Timer, the Presets list or a workout's summary; [New] starts a preset from them without
 * touching the Timer. [Timer] shapes the workout on the Timer itself, a sequence's blocks and all: Done puts it
 * there and saves nothing, and Save as preset turns it into [SaveCurrent].
 */
enum class EditorMode { Edit, New, Copy, SaveCurrent, Timer }

/**
 * A preset as the editor has it: the shape being set, the name it opened with (the field holds what
 * is typed after that) and, for one of the user's own being edited, its [id]; whether it [alternateHands], which
 * only a kettlebell preset keeps ([handsKept]).
 */
data class PresetDraft(
    val mode: EditorMode,
    val name: String,
    val config: IntervalConfig,
    val id: Long? = null,
    val alternateHands: Boolean = false,
) {
    /** Whether the preset saved from this draft switches hands: only one worked with a bell can. */
    val handsKept: Boolean get() = alternateHands && config.activity == Activity.Kettlebell

    companion object {
        /** A new, unnamed preset in [config]'s shape, to save the settings on the Timer. */
        fun saveCurrent(config: IntervalConfig) = PresetDraft(EditorMode.SaveCurrent, name = "", config = config)

        /** [preset], one of the user's own, to edit in place. */
        fun edit(preset: Preset) =
            PresetDraft(EditorMode.Edit, preset.name, preset.config, preset.id, preset.alternateHands)

        /** A copy of [preset] to save under [name]. */
        fun copy(preset: Preset, name: String) =
            PresetDraft(EditorMode.Copy, name, preset.config, alternateHands = preset.alternateHands)

        /** The workout on the Timer, [config], to shape in place. */
        fun timer(config: IntervalConfig) = PresetDraft(EditorMode.Timer, name = "", config = config)
    }
}

/**
 * Names and shapes a preset in a sheet: the name with its count, a card of rows for each block (Work, Rest and
 * Rounds, the ladder and the sets; one for a workout of one block), Add a block, then the rest between blocks, the
 * warm-up, cool-down and activity, each row opening its edit sheet; the total it comes to with the user's get-ready
 * countdown, and Save. A sequence's blocks each say their number and move up or down or go. A blank name saves as
 * the shape's own ("40/20 ×6"), which the empty field shows. Before Save it says what the repository will do with a
 * name another of [presets] has ("Saves as Tabata 2") and which one already has this shape. Each change to the shape
 * goes back through [onChangeShape], so the [draft] lives with its caller. In [EditorMode.Timer] there is no name:
 * Done hands the shape to [onSave], and Save as preset goes to [onSaveAsPreset].
 */
@Composable
internal fun PresetEditor(
    draft: PresetDraft,
    prepSeconds: Int,
    presets: List<Preset>,
    onChangeShape: ((IntervalConfig) -> IntervalConfig) -> Unit,
    onSave: (Preset) -> Unit,
    onDismiss: () -> Unit,
    onSaveAsPreset: () -> Unit = {},
    onAlternateHands: (Boolean) -> Unit = {},
) {
    val palette = IntervalTheme.palette
    val name = rememberTextFieldState(draft.name)
    val placeholder = draft.config.defaultPresetName()
    val total = formatClock(draft.config.copy(prepSeconds = prepSeconds).totalSeconds)
    val title = stringResource(draft.mode.titleRes())
    // The one being edited is not "another" preset: keeping its own name or shape is no clash.
    val others = presets.filterNot { draft.id != null && it.id == draft.id }
    // What the user means, before another preset's name makes it numbered: shown when the two differ.
    val wanted = name.text.toString().trim().ifEmpty { placeholder }
    val storedName = Preset(wanted, draft.config, draft.id).storedName(presets)
    val sameShape = others.firstOrNull { it.config.sameShape(draft.config) }
    val timer = draft.mode == EditorMode.Timer
    // The block and the field being edited, as "1:Work": saved as text, which every platform can.
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingKey?.let(::editingOf)
    var choosingActivity by rememberSaveable { mutableStateOf(false) }
    val notices = LocalNotices.current
    val savedText = stringResource(Res.string.notice_preset_saved, storedName)
    IntervalSheet(title = title, onDismiss = onDismiss) { hide ->
        val save = {
            onSave(Preset(wanted, draft.config, draft.id, alternateHands = draft.handsKept))
            if (!timer) notices.show(savedText)
            hide()
        }
        EditorHeader(title, onCancel = hide)
        if (!timer) {
            EditorName(name, placeholder, storedName, wanted, save)
        }
        EditorBlocks(draft.config, { block, field -> editingKey = "$block:${field.name}" }, onChangeShape)
        EditorWorkoutCard(
            draft.config,
            { editingKey = "0:${it.name}" },
            { choosingActivity = true },
            hands = draft.alternateHands.takeIf { !timer && draft.config.activity == Activity.Kettlebell },
            onHands = onAlternateHands,
        )
        EditorTotal(total)
        if (!timer) sameShape?.let { EditorHint(stringResource(Res.string.preset_editor_same_shape, it.name)) }
        if (timer) {
            AccentButton(stringResource(Res.string.common_done), onClick = save, modifier = Modifier.fillMaxWidth())
            QuietTextButton(
                stringResource(Res.string.preset_editor_save_as_preset),
                onClick = onSaveAsPreset,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        } else {
            AccentButton(stringResource(Res.string.common_save), onClick = save, modifier = Modifier.fillMaxWidth())
        }
    }
    editing?.let { (block, field) ->
        ValueSheet(
            field = field,
            value = draft.config.valueOf(field, block),
            total = total,
            onStep = { direction -> onChangeShape { it.stepped(field, direction, block) } },
            onPick = { value -> onChangeShape { it.withValue(field, value, block) } },
            onDismiss = { editingKey = null },
        )
    }
    if (choosingActivity) {
        ActivitySheet(
            selected = draft.config.activity,
            onSelect = { activity -> onChangeShape { it.copy(activity = activity) } },
            onDismiss = { choosingActivity = false },
        )
    }
}

/** The block (0-based) and field [key] names, "1:Work"; null for one this build can't read. */
private fun editingOf(key: String): Pair<Int, ConfigField>? {
    val parts = key.split(':')
    val block = parts.first().toIntOrNull()
    val field = ConfigField.entries.firstOrNull { it.name == parts.getOrNull(1) }
    return if (parts.size == 2 && block != null && field != null) block to field else null
}

/** The name with its count, the field, and what Save will call it when that differs from what is typed. */
@Composable
private fun EditorName(
    name: TextFieldState,
    placeholder: String,
    storedName: String,
    wanted: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.space4), verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(Res.string.preset_name_label),
                style = IntervalTheme.type.caption,
                color = palette.textDim,
                modifier = Modifier.weight(1f).clearAndSetSemantics {},
            )
            Text(
                stringResource(Res.string.preset_name_count, name.text.length, Preset.MAX_NAME_LENGTH),
                style = IntervalTheme.type.caption,
                color = palette.textDim,
            )
        }
        NameField(
            name,
            placeholder,
            stringResource(Res.string.preset_name_label),
            Preset.MAX_NAME_LENGTH,
            onDone = onDone,
            capitalization = KeyboardCapitalization.Words,
        )
        if (storedName != wanted) EditorHint(stringResource(Res.string.preset_editor_saves_as, storedName))
    }
}

private fun EditorMode.titleRes() = when (this) {
    EditorMode.Edit -> Res.string.preset_editor_edit_title
    EditorMode.New -> Res.string.preset_editor_new_title
    EditorMode.Copy -> Res.string.preset_editor_copy_title
    EditorMode.SaveCurrent -> Res.string.preset_save_title
    EditorMode.Timer -> Res.string.preset_editor_timer_title
}

/**
 * A card per block of [config]: its rows, under its number and the ways to move it or take it out when there is
 * more than one; then Add a block, while there is room for one.
 */
@Composable
private fun EditorBlocks(
    config: IntervalConfig,
    onEditField: (Int, ConfigField) -> Unit,
    onChangeShape: ((IntervalConfig) -> IntervalConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        config.blocks.forEachIndexed { index, block ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(Corners.radius18)
                    .background(IntervalTheme.palette.surfaceVariant)
                    // Each block's rows read after its number, so "Edit work" says which block's.
                    .semantics { isTraversalGroup = true },
            ) {
                if (config.isSequence) {
                    BlockHeader(
                        number = index + 1,
                        first = index == 0,
                        last = index == config.blocks.lastIndex,
                        onMove = { by -> onChangeShape { it.withBlockMoved(index, by) } },
                        onRemove = { onChangeShape { it.withoutBlock(index) } },
                    )
                    RowDivider()
                }
                ConfigField.blockFields(block).forEachIndexed { row, field ->
                    if (row > 0) RowDivider()
                    FieldRow(field, block.valueOf(field), onClick = { onEditField(index, field) })
                }
            }
        }
        if (config.blocks.size < IntervalConfig.MAX_BLOCKS) {
            QuietTextButton(
                stringResource(Res.string.preset_editor_add_block),
                onClick = { onChangeShape { it.withBlockAdded() } },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** "Block 2", a heading, with the ways to move it earlier or later and to take it out. */
@Composable
private fun BlockHeader(
    number: Int,
    first: Boolean,
    last: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(start = Spacing.space16, end = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.preset_editor_block, number),
            style = IntervalTheme.type.label,
            color = IntervalTheme.palette.text,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (!first) {
            IconAction(IntervalIcons.ChevronUp, stringResource(Res.string.preset_editor_move_up, number), {
                onMove(-1)
            })
        }
        if (!last) {
            IconAction(
                IntervalIcons.ChevronDown,
                stringResource(Res.string.preset_editor_move_down, number),
                { onMove(+1) },
            )
        }
        IconAction(IntervalIcons.Close, stringResource(Res.string.preset_editor_remove_block, number), onRemove)
    }
}

/**
 * What is the whole workout's rather than a block's: the rest between blocks, the bookends and the activity, and
 * for a kettlebell preset whether it switches [hands] each work (null where it can't).
 */
@Composable
private fun EditorWorkoutCard(
    config: IntervalConfig,
    onEditField: (ConfigField) -> Unit,
    onChooseActivity: () -> Unit,
    modifier: Modifier = Modifier,
    hands: Boolean? = null,
    onHands: (Boolean) -> Unit = {},
) {
    Column(modifier.fillMaxWidth().clip(Corners.radius18).background(IntervalTheme.palette.surfaceVariant)) {
        val fields = listOfNotNull(ConfigField.BlockRest.takeIf { config.isSequence }) + ConfigField.bookends
        fields.forEachIndexed { index, field ->
            if (index > 0) RowDivider()
            FieldRow(field, config.valueOf(field), onClick = { onEditField(field) })
        }
        RowDivider()
        LinkRow(
            stringResource(Res.string.activity_title),
            stringResource(config.activity.labelRes),
            onClick = onChooseActivity,
        )
        if (hands != null) {
            RowDivider()
            ToggleRow(
                stringResource(Res.string.preset_editor_switch_hands),
                stringResource(Res.string.preset_editor_switch_hands_text),
                checked = hands,
                onToggle = { onHands(!hands) },
            )
        }
    }
}

/** Cancel at the start and the [title] centred in a row so large font scales never collide. */
@Composable
private fun EditorHeader(title: String, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        QuietTextButton(stringResource(Res.string.common_cancel), onClick = onCancel)
        Text(
            title,
            style = IntervalTheme.type.value,
            color = IntervalTheme.palette.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Sizes.pillWidth))
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
fun rememberPresetDraft(): MutableState<PresetDraft?> =
    rememberSaveable(stateSaver = PresetDraftSaver) { mutableStateOf(null) }

/**
 * The [PresetEditor] for whichever [draft] a screen has opened, if any. It reshapes the draft through
 * [onDraftChange] as the user edits and clears it once the editor closes, so a screen opens the editor
 * by setting its draft and does nothing else.
 */
@Composable
fun PresetEditorHost(
    draft: PresetDraft?,
    onDraftChange: (PresetDraft?) -> Unit,
    prepSeconds: Int,
    presets: List<Preset>,
    onSave: (PresetDraft, Preset) -> Unit,
) {
    if (draft == null) return
    PresetEditor(
        draft = draft,
        prepSeconds = prepSeconds,
        presets = presets,
        onChangeShape = { transform -> onDraftChange(draft.copy(config = transform(draft.config))) },
        onSave = { onSave(draft, it) },
        onDismiss = { onDraftChange(null) },
        onSaveAsPreset = { onDraftChange(draft.copy(mode = EditorMode.SaveCurrent)) },
        onAlternateHands = { onDraftChange(draft.copy(alternateHands = it)) },
    )
}

/** A preset saved by its values, as a dialog's open state is. */
internal val PresetSaver: Saver<Preset?, Any> = mapSaver(
    save = { preset ->
        if (preset == null) {
            emptyMap()
        } else {
            mapOf(NAME to preset.name, ID to preset.id, HANDS to preset.alternateHands) + preset.config.saved()
        }
    },
    restore = { Preset(it[NAME] as String, it.restoredConfig(), it[ID] as Long?, it[HANDS] as Boolean? ?: false) },
)

private val PresetDraftSaver: Saver<PresetDraft?, Any> = mapSaver(
    save = { draft ->
        if (draft == null) {
            emptyMap()
        } else {
            mapOf(MODE to draft.mode.name, NAME to draft.name, ID to draft.id, HANDS to draft.alternateHands) +
                draft.config.saved()
        }
    },
    restore = {
        PresetDraft(
            EditorMode.valueOf(it[MODE] as String),
            it[NAME] as String,
            it.restoredConfig(),
            it[ID] as Long?,
            it[HANDS] as Boolean? ?: false,
        )
    },
)

private fun IntervalConfig.saved(): Map<String, Any?> = mapOf(
    PREP to prepSeconds,
    WORK to workSeconds,
    REST to restSeconds,
    ROUNDS to rounds,
    WARM_UP to warmUpSeconds,
    COOL_DOWN to coolDownSeconds,
    ACTIVITY to activity.name,
    SETS to sets,
    SET_REST to setRestSeconds,
    WORK_STEP to workStepSeconds,
    // The blocks after the first, flat: a Bundle keeps an IntArray, not a list of them.
    THEN to then.flatMap {
        listOf(it.workSeconds, it.restSeconds, it.rounds, it.sets, it.setRestSeconds, it.workStepSeconds)
    }.toIntArray(),
    BLOCK_REST to blockRestSeconds,
)

private fun Map<String, Any?>.restoredConfig(): IntervalConfig =
    IntervalConfig.sanitized(
        prepSeconds = this[PREP] as Int,
        workSeconds = this[WORK] as Int,
        restSeconds = this[REST] as Int,
        rounds = this[ROUNDS] as Int,
        warmUpSeconds = this[WARM_UP] as Int? ?: 0,
        coolDownSeconds = this[COOL_DOWN] as Int? ?: 0,
        activity = Activity.named(this[ACTIVITY] as String?),
        sets = this[SETS] as Int? ?: 1,
        setRestSeconds = this[SET_REST] as Int? ?: 0,
        workStepSeconds = this[WORK_STEP] as Int? ?: 0,
        then = (this[THEN] as IntArray? ?: IntArray(0)).toList().chunked(BLOCK_VALUES)
            .filter { it.size == BLOCK_VALUES }
            .map { values ->
                val next = values.iterator()
                Block.sanitized(next.next(), next.next(), next.next(), next.next(), next.next(), next.next())
            },
        blockRestSeconds = this[BLOCK_REST] as Int? ?: 0,
    )

private const val MODE = "mode"
private const val NAME = "name"
private const val ID = "id"
private const val HANDS = "alternate_hands"
private const val PREP = "prep"
private const val WORK = "work"
private const val REST = "rest"
private const val ROUNDS = "rounds"
private const val WARM_UP = "warm_up"
private const val COOL_DOWN = "cool_down"
private const val ACTIVITY = "activity"
private const val SETS = "sets"
private const val SET_REST = "set_rest"
private const val WORK_STEP = "work_step"
private const val THEN = "then"
private const val BLOCK_REST = "block_rest"
private const val BLOCK_VALUES = 6
