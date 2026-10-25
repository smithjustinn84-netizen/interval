package dev.justinsmith.bells.session

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.justinsmith.bells.domain.CustomExercise
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.HoldPhase
import dev.justinsmith.bells.domain.HoldState
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.library.ExerciseDetailContent
import dev.justinsmith.bells.library.ExerciseDetailUiState
import dev.justinsmith.bells.library.ExerciseEditorForm
import dev.justinsmith.bells.library.ExercisePicker
import dev.justinsmith.bells.library.rememberExerciseEditor
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.emom_fewer_minutes
import dev.justinsmith.bells.resources.emom_get_ready
import dev.justinsmith.bells.resources.emom_minutes
import dev.justinsmith.bells.resources.emom_more_minutes
import dev.justinsmith.bells.resources.emom_start
import dev.justinsmith.bells.resources.exercise_editor_new
import dev.justinsmith.bells.resources.hold_hold
import dev.justinsmith.bells.resources.hold_other_side
import dev.justinsmith.bells.resources.hold_stop
import dev.justinsmith.bells.resources.hold_stop_note
import dev.justinsmith.bells.resources.hold_time_it
import dev.justinsmith.bells.resources.log_back_to_set
import dev.justinsmith.bells.resources.log_change
import dev.justinsmith.bells.resources.log_edit_save
import dev.justinsmith.bells.resources.log_edit_title
import dev.justinsmith.bells.resources.log_exercise
import dev.justinsmith.bells.resources.log_fewer_reps
import dev.justinsmith.bells.resources.log_first_side
import dev.justinsmith.bells.resources.log_held
import dev.justinsmith.bells.resources.log_kind_emom
import dev.justinsmith.bells.resources.log_kind_set
import dev.justinsmith.bells.resources.log_less_time
import dev.justinsmith.bells.resources.log_more_reps
import dev.justinsmith.bells.resources.log_more_time
import dev.justinsmith.bells.resources.log_reps
import dev.justinsmith.bells.resources.log_reps_each_minute
import dev.justinsmith.bells.resources.log_side
import dev.justinsmith.bells.resources.log_title
import dev.justinsmith.bells.resources.next_how_to
import dev.justinsmith.bells.resources.session_log
import dev.justinsmith.bells.resources.summary_done
import dev.justinsmith.bells.ui.ChoiceRow
import dev.justinsmith.bells.ui.LoadPicker
import dev.justinsmith.bells.ui.Stepper
import dev.justinsmith.bells.ui.exerciseFacts
import dev.justinsmith.bells.ui.exerciseName
import dev.justinsmith.bells.ui.sideName
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.ModalBackHandler
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RollingText
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.SegmentedPicker
import dev.justinsmith.interval.ui.SheetColumns
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.sheetIsSideways
import org.jetbrains.compose.resources.stringResource

/** What the Log sheet adds: one set now, or an EMOM of the set every minute. */
private enum class LogKind { Set, Emom }

/**
 * Pick a set or an EMOM, then what it is done with and the exercise, its load (a bell or two, weight added
 * to bodyweight, a band, or none on the mat) and its reps, or for a hold how long; for a set, the side of a
 * one-sided exercise, for an EMOM its minutes and first side. A hold is only ever a set. Then log the set, or
 * start the EMOM. A hold can also be timed here: the sheet counts it down and closes once it is logged, unless
 * it offers the other side. Closing it mid-hold drops the hold.
 */
@Composable
internal fun LogSetSheet(state: SessionUiState, actions: SessionActions, onDismiss: () -> Unit) {
    val draft = state.draft
    val editing = state.editing != null
    // Saved by name: an enum is not saveable on every platform.
    var kindName by rememberSaveable { mutableStateOf(LogKind.Set.name) }
    val kind = if (draft.held || editing) LogKind.Set else LogKind.valueOf(kindName)
    // Change swaps the sheet for the library until an exercise is picked, and the library's Add for a new
    // exercise of that name until it is saved, when the sheet comes back with it picked.
    var picking by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    // How to swaps the set for the exercise's page until Back to the set.
    var reading by rememberSaveable { mutableStateOf(false) }
    val onClose = {
        actions.onOpenExercise(null)
        actions.onCancelHold()
        actions.onCancelEdit()
        onDismiss()
    }
    val sideways = sheetIsSideways()
    IntervalSheet(logSheetTitle(draft.exercise, reading, adding != null, editing), onDismiss = onClose) { hide ->
        val hold = state.hold
        val holdState = state.holdState
        CloseWhenTimed(timing = hold != null, offering = state.otherSide != null, hide)
        if (hold != null && holdState != null) {
            HoldCountdown(hold, holdState, actions.onStopHold)
            return@IntervalSheet
        }
        state.detail?.takeIf { reading }?.let { detail ->
            ExercisePage(detail, onBack = { reading = false }, actions)
            return@IntervalSheet
        }
        if (picking || adding != null) {
            ExerciseChoice(
                state,
                actions,
                adding,
                onPick = { picking = false },
                onAdd = {
                    picking = false
                    adding = it
                },
                onSave = { adding = null },
            )
            return@IntervalSheet
        }
        // What the set is first, then how much of it and the action, side by side on a phone on its side.
        val what: @Composable ColumnScope.() -> Unit = {
            if (!draft.held && !editing) {
                SegmentedPicker(
                    LogKind.entries,
                    kind,
                    label = {
                        stringResource(if (it == LogKind.Set) Res.string.log_kind_set else Res.string.log_kind_emom)
                    },
                    onSelect = { kindName = it.name },
                    modifier = Modifier.padding(bottom = Spacing.space8),
                )
            }
            // A set logged keeps its exercise: what it was done with is what it was.
            ExerciseCard(
                draft.exercise,
                onChange = if (editing) null else ({ picking = true }),
                onHowTo = {
                    actions.onOpenExercise(draft.exercise.key)
                    reading = true
                },
            )
            DraftLoad(state, actions)
        }
        val howMuch: @Composable ColumnScope.() -> Unit = {
            MeasurePicker(draft, emom = kind == LogKind.Emom, state.otherSide, actions, timing = !editing)
            if (kind == LogKind.Emom) EmomMinutes(draft, actions)
            if (draft.exercise.sided(draft.doubled)) SidePicker(draft, first = kind == LogKind.Emom, actions)
            SheetAction(kind, editing, actions, hide)
        }
        SheetColumns(
            sideways,
            first = what,
            second = howMuch,
            modifier = Modifier.padding(horizontal = Spacing.space20, vertical = Spacing.space8),
        )
    }
}

/**
 * What the Log sheet shows in place of the set, and the Next set card's sheet shows, while the exercise is
 * being chosen: the library, until one is picked ([onPick]) or Add starts a new one by its name ([onAdd]);
 * then, while [adding], that exercise's form, until it is saved ([onSave]) and comes back picked.
 */
@Composable
internal fun ExerciseChoice(
    state: SessionUiState,
    actions: SessionActions,
    adding: String?,
    onPick: () -> Unit,
    onAdd: (name: String) -> Unit,
    onSave: () -> Unit,
) {
    val modifier = Modifier.padding(horizontal = Spacing.space20, vertical = Spacing.space8)
    if (adding == null) {
        ExercisePicker(
            state.library,
            actions.onFilters,
            modifier,
            selected = state.draft.exercise,
            onPick = { e ->
                actions.onDraft { it.copy(exercise = e) }
                onPick()
            },
            onAdd = { name ->
                actions.onAddExercise()
                onAdd(name)
            },
        )
    } else {
        val editor = rememberExerciseEditor(uid = null, name = adding)
        ExerciseEditorForm(
            editor.state,
            onChange = editor::change,
            onSave = { typed, cues -> if (editor.save(typed, cues)) onSave() },
            modifier = modifier,
        )
    }
}

/** The Log sheet's title: the exercise while its page is up, else what the sheet is doing. */
@Composable
private fun logSheetTitle(exercise: Exercise, reading: Boolean, adding: Boolean, editing: Boolean): String =
    if (reading) {
        exerciseName(exercise)
    } else {
        stringResource(
            when {
                adding -> Res.string.exercise_editor_new
                editing -> Res.string.log_edit_title
                else -> Res.string.log_title
            },
        )
    }

/**
 * The exercise's page in place of the set, with no Edit and no sessions to open from here, and Back to the set
 * ([onBack]), which the back gesture also does; either closes the page.
 */
@Composable
private fun ExercisePage(detail: ExerciseDetailUiState, onBack: () -> Unit, actions: SessionActions) {
    val back = {
        onBack()
        actions.onOpenExercise(null)
    }
    ModalBackHandler(back)
    Column(Modifier.padding(horizontal = Spacing.space20, vertical = Spacing.space8)) {
        ExerciseDetailContent(detail, onEdit = null, onOpenSession = null)
        SecondaryButton(
            stringResource(Res.string.log_back_to_set),
            back,
            Modifier.fillMaxWidth().padding(top = Spacing.space16, bottom = Spacing.space8),
        )
    }
}

/** The side of a one-sided exercise: the set's, or an EMOM's [first]. */
@Composable
private fun SidePicker(draft: SetDraft, first: Boolean, actions: SessionActions) {
    GroupLabel(stringResource(if (first) Res.string.log_first_side else Res.string.log_side))
    ChoiceRow(Side.entries, draft.side, { sideName(it) }, { side -> actions.onDraft { it.copy(side = side) } })
}

/** What the sheet does last: Save set for a set logged, Log set, or Start EMOM; then it closes. */
@Composable
private fun SheetAction(kind: LogKind, editing: Boolean, actions: SessionActions, hide: () -> Unit) {
    AccentButton(
        stringResource(
            when {
                editing -> Res.string.log_edit_save
                kind == LogKind.Set -> Res.string.session_log
                else -> Res.string.emom_start
            },
        ),
        onClick = {
            when {
                editing -> actions.onSaveEdit()
                kind == LogKind.Set -> actions.onLog()
                else -> actions.onStartEmom()
            }
            hide()
        },
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.space16, bottom = Spacing.space8),
    )
}

/**
 * The exercise the set is of, its facts under its name, How to ([onHowTo]) for its page, and Change, with
 * [onChange], to pick another.
 */
@Composable
internal fun ExerciseCard(exercise: Exercise, onChange: (() -> Unit)?, onHowTo: (() -> Unit)? = null) {
    val palette = IntervalTheme.palette
    ListCard {
        Row(
            Modifier.padding(
                start = Spacing.space16,
                end = Spacing.space4,
                top = Spacing.space12,
                bottom = Spacing.space12,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.log_exercise),
                    style = IntervalTheme.type.caption,
                    color = palette.textDim,
                )
                Text(exerciseName(exercise), style = IntervalTheme.type.section, color = palette.text)
                Text(exerciseFacts(exercise), style = IntervalTheme.type.meta, color = palette.textDim)
                (exercise as? CustomExercise)?.cues?.takeIf { it.isNotBlank() }?.let { cues ->
                    Text(cues, style = IntervalTheme.type.meta, color = palette.text)
                }
                onHowTo?.let { QuietTextButton(stringResource(Res.string.next_how_to), it) }
            }
            onChange?.let { QuietTextButton(stringResource(Res.string.log_change), it) }
        }
    }
}

/**
 * How much of it: the reps (each minute, for an EMOM), or for a hold how long, in steps of five seconds, with
 * Time it to count it down; after a one-sided hold's first side is timed, the [otherSide] for the same length.
 */
@Composable
private fun MeasurePicker(
    draft: SetDraft,
    emom: Boolean,
    otherSide: Side?,
    actions: SessionActions,
    timing: Boolean = true,
) {
    if (draft.held) {
        // One column: the length, then Time it under it (beside the steppers it squeezed the clock onto two
        // lines on a phone), then the other side when there is one.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            GroupLabel(stringResource(Res.string.log_held))
            Stepper(
                formatClock(draft.seconds),
                fewer = stringResource(Res.string.log_less_time),
                more = stringResource(Res.string.log_more_time),
                canLower = draft.seconds > SetDraft.SECONDS_STEP,
                canRaise = draft.seconds < SetDraft.MAX_SECONDS,
                onStep = { step ->
                    actions.onDraft { it.copy(seconds = it.seconds + step * SetDraft.SECONDS_STEP) }
                },
            )
            if (timing) {
                SecondaryButton(stringResource(Res.string.hold_time_it), actions.onTimeHold, Modifier.fillMaxWidth())
            }
            otherSide?.takeIf { timing }?.let { side ->
                AccentButton(
                    stringResource(Res.string.hold_other_side, sideName(side)),
                    actions.onTimeHold,
                    Modifier.fillMaxWidth(),
                )
            }
        }
    } else {
        GroupLabel(
            stringResource(if (!emom) Res.string.log_reps else Res.string.log_reps_each_minute),
        )
        CountStepper(
            draft.reps,
            fewer = stringResource(Res.string.log_fewer_reps),
            more = stringResource(Res.string.log_more_reps),
            max = SetDraft.MAX_REPS,
            onStep = { step -> actions.onDraft { it.copy(reps = it.reps + step) } },
        )
    }
}

/** The load the set takes, from the draft: a bell (or two), the bar, weight added to bodyweight, a band, or none. */
@Composable
private fun DraftLoad(state: SessionUiState, actions: SessionActions) {
    val draft = state.draft
    LoadPicker(
        draft.exercise,
        draft.double,
        kg = when (draft.exercise.equipment) {
            Equipment.Bodyweight -> draft.addedKg
            Equipment.Barbell -> draft.barKg
            else -> draft.bellKg
        },
        band = draft.band,
        bells = state.bells,
        bands = state.bands,
        onKg = { kg ->
            kg?.let { picked ->
                actions.onDraft {
                    when (it.exercise.equipment) {
                        Equipment.Bodyweight -> it.copy(addedKg = picked)
                        Equipment.Barbell -> it.copy(barKg = picked)
                        else -> it.copy(bellKg = picked)
                    }
                }
            }
        },
        onBand = { band -> band?.let { picked -> actions.onDraft { it.copy(band = picked) } } },
        onDouble = { actions.onDraft { it.copy(double = !it.double) } },
        ownsBells = state.ownsBells,
        bar = state.bar,
    )
}

/** A count from 1 to [max], large, with − and + that repeat while held. */
@Composable
private fun CountStepper(value: Int, fewer: String, more: String, max: Int, onStep: (Int) -> Unit) {
    Stepper(value.toString(), fewer, more, canLower = value > 1, canRaise = value < max, onStep = onStep)
}

/** How many minutes an EMOM started from the sheet runs. */
@Composable
private fun EmomMinutes(draft: SetDraft, actions: SessionActions) {
    GroupLabel(stringResource(Res.string.emom_minutes))
    CountStepper(
        draft.minutes,
        fewer = stringResource(Res.string.emom_fewer_minutes),
        more = stringResource(Res.string.emom_more_minutes),
        max = SetDraft.MAX_MINUTES,
        onStep = { step -> actions.onDraft { it.copy(minutes = it.minutes + step) } },
    )
}

/** Once a timed hold is logged the sheet's work is done, as after Log set, unless it offers the other side. */
@Composable
private fun CloseWhenTimed(timing: Boolean, offering: Boolean, hide: () -> Unit) {
    var wasTiming by remember { mutableStateOf(timing) }
    val close by rememberUpdatedState(hide)
    LaunchedEffect(timing) {
        if (wasTiming && !timing && !offering) close()
        wasTiming = timing
    }
}

/**
 * A hold counting down, in the workout's colours: amber through the count-in, red while held. The clock
 * rolls each second; a haptic marks each phase, the beeps come from the view model. Stop logs what was held.
 */
@Composable
private fun HoldCountdown(run: HoldRun, state: HoldState, onStop: () -> Unit) {
    val colors = IntervalTheme.workout
    val phaseColor = when (state.phase) {
        HoldPhase.GetReady -> colors.prep
        HoldPhase.Hold -> colors.work
        HoldPhase.Done -> colors.done
    }
    val color by animateColorAsState(phaseColor, tween(Motion.PHASE_CROSSFADE_MILLIS))
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.phase) { haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.space20, vertical = Spacing.space16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Text(
            stringResource(
                when (state.phase) {
                    HoldPhase.GetReady -> Res.string.emom_get_ready
                    HoldPhase.Hold -> Res.string.hold_hold
                    HoldPhase.Done -> Res.string.summary_done
                },
            ),
            style = IntervalTheme.type.stat,
            color = color,
        )
        RollingText(
            formatClock(state.secondsLeft),
            rising = false,
            style = IntervalTheme.type.display,
            color = IntervalTheme.palette.text,
        )
        val set = run.set
        Text(
            if (set.exercise.sided(set.doubled)) {
                "${exerciseName(set.exercise)} · ${sideName(set.side)}"
            } else {
                exerciseName(set.exercise)
            },
            style = IntervalTheme.type.label,
            color = IntervalTheme.palette.textDim,
        )
        SecondaryButton(
            stringResource(Res.string.hold_stop),
            onStop,
            Modifier.fillMaxWidth().padding(top = Spacing.space16),
        )
        Text(
            stringResource(Res.string.hold_stop_note),
            style = IntervalTheme.type.meta,
            color = IntervalTheme.palette.textDim,
        )
    }
}
