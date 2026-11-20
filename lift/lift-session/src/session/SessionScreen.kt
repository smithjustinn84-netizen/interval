package dev.justinsmith.lift.session

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.justinsmith.interval.platform.AskToShowNotifications
import dev.justinsmith.interval.platform.KeepScreenOn
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.HeartBeatBadge
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LinkRow
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.NoticeAction
import dev.justinsmith.interval.ui.Notices
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TitleBar
import dev.justinsmith.lift.domain.Block
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.ExerciseFilters
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.RestState
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.library.ExerciseDetailSheet
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.common_cancel
import dev.justinsmith.lift.session.resources.next_program_day
import dev.justinsmith.lift.session.resources.next_program_day_hint
import dev.justinsmith.lift.session.resources.notice_record
import dev.justinsmith.lift.session.resources.session_add_cd
import dev.justinsmith.lift.session.resources.session_discard
import dev.justinsmith.lift.session.resources.session_discard_text
import dev.justinsmith.lift.session.resources.session_discard_title
import dev.justinsmith.lift.session.resources.session_title
import dev.justinsmith.lift.session.resources.session_undo
import dev.justinsmith.lift.ui.demo.Celebration
import dev.justinsmith.lift.ui.setLine
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** What the session screen's controls do. */
data class SessionActions(
    val onDraft: ((SetDraft) -> SetDraft) -> Unit = {},
    val onFilters: ((ExerciseFilters) -> ExerciseFilters) -> Unit = {},
    // The Log sheet is off to add an exercise of the user's: once it is saved, the sheet has it picked.
    val onAddExercise: () -> Unit = {},
    val onLog: () -> Unit = {},
    val onLogNext: () -> Unit = {},
    /** The Log sheet opening: on a program day it starts on the set up next. */
    val onOpenLog: () -> Unit = {},
    /** The next set's load a step lighter (−1) or heavier (+1), for the rest of its block today. */
    val onStepLoad: (Int) -> Unit = {},
    /** The sheet's draft a step lighter (−1) or heavier (+1): the Next set card's load in a free session. */
    val onStepDraftLoad: (Int) -> Unit = {},
    /** An exercise's page, by its key, opened; null closes it. */
    val onOpenExercise: (String?) -> Unit = {},
    /** The plan's next block started on its clock: an EMOM or a density block. */
    val onStartPlannedClock: () -> Unit = {},
    val onTimePlannedHold: () -> Unit = {},
    val onStartEmom: () -> Unit = {},
    /** Times the sheet's hold; Stop logs the seconds held, Cancel drops it. */
    val onTimeHold: () -> Unit = {},
    val onStopHold: () -> Unit = {},
    val onCancelHold: () -> Unit = {},
    val onRemoveSet: (Long) -> Unit = {},
    val onRestoreSet: (SetLog) -> Unit = {},
    /** A set logged opened on the sheet to change, then saved or left as it was. */
    val onEditSet: (Long) -> Unit = {},
    val onSaveEdit: () -> Unit = {},
    val onCancelEdit: () -> Unit = {},
    /** Today's plan: block [Int] changed, given another exercise, skipped, or brought back. */
    val onChangeBlock: (Int, (Block) -> Block) -> Unit = { _, _ -> },
    val onSwapBlock: (Int, Exercise) -> Unit = { _, _ -> },
    val onSkipBlock: (Int) -> Unit = {},
    val onUnskipBlock: (Int) -> Unit = {},
    /** The rest a step shorter (−1) or longer (+1), and every one after; or skipped. */
    val onStepRest: (Int) -> Unit = {},
    /** Takes the easier day offered on a taxed one. */
    val onUseEasier: () -> Unit = {},
    /** A free session made day [Int] (from 0) of [Program]: see [TodayPlanEditor.pickDay]. */
    val onPickDay: (Program, Int) -> Unit = { _, _ -> },
    val onSkipRest: () -> Unit = {},
    /** Adds the reps of the round a test's cap cut short. */
    val onLogPartRound: (Int) -> Unit = {},
    val onFinish: () -> Unit = {},
    val onDiscard: () -> Unit = {},
)

/**
 * The session in progress: its time and weight moved, what is next and what is done, + in the title bar for the
 * Log sheet (another set or an EMOM), and Log set or Finish at the foot. [clocks] are read only where drawn.
 */
@Composable
fun SessionScreen(
    state: SessionUiState,
    clocks: () -> SessionClocks,
    actions: SessionActions,
    modifier: Modifier = Modifier,
) {
    var logging by rememberSaveable { mutableStateOf(false) }
    var discarding by rememberSaveable { mutableStateOf(false) }
    // Finish with nothing logged: the same ask as Back, since there is nothing to save.
    var leaving by rememberSaveable { mutableStateOf(false) }
    var planning by rememberSaveable { mutableStateOf(false) }
    // The exercise's page open over the session, from Up next or the Next set card.
    var reading by rememberSaveable { mutableStateOf(false) }
    // The library open over a free session, to pick the next set's lift.
    var picking by rememberSaveable { mutableStateOf(false) }
    // The programs open over a free session, to take a day of one.
    var pickingDay by rememberSaveable { mutableStateOf(false) }
    // The block of today's plan open to change, -1 for none: a plain number saves on every platform.
    var changing by rememberSaveable { mutableIntStateOf(-1) }
    KeepScreenOn()
    // The rest's notification off screen needs the platform's leave, asked as the first rest starts.
    if (state.asksToShowNotifications) AskToShowNotifications()
    val notices = LocalNotices.current
    val undo = stringResource(Res.string.session_undo)
    // The record set whose moment is on, for the next lift's figure to mark; null between moments.
    var celebrating by remember { mutableStateOf<Long?>(null) }
    AnnounceRecord(state, notices, onCelebrate = { celebrating = it })
    val openSheet = {
        actions.onOpenLog()
        logging = true
    }
    val sets = SetActions(
        // The set just removed is offered back for a few seconds.
        onRemove = { set, removedText ->
            actions.onRemoveSet(set.id)
            notices.show(removedText, NoticeAction(undo) { actions.onRestoreSet(set) })
        },
        onEdit = {
            actions.onEditSet(it.id)
            logging = true
        },
    )
    val parts = SessionParts(
        bar = {
            TitleBar(
                stringResource(Res.string.session_title),
                navigation = {
                    IconAction(IntervalIcons.Close, stringResource(Res.string.session_discard), { discarding = true })
                },
                actions = {
                    HeartBeatBadge(clocks().liveHeartRate, onWorkoutGround = false)
                    IconAction(IntervalIcons.Add, stringResource(Res.string.session_add_cd), openSheet)
                },
            )
        },
        tiles = { wide -> SessionTiles(state, clocks, wide) },
        plan = {
            SessionPlanContent(
                state = state,
                actions = actions,
                sets = sets,
                onRead = { reading = true },
                onPick = { picking = true },
                onPickDay = { pickingDay = true },
                onChange = { changing = it },
                onPlan = { planning = true },
                onLog = { logging = true },
                celebrating = celebrating,
            )
        },
        foot = {
            SessionFoot(
                state,
                clocks,
                actions,
                onFinish = { if (state.session?.sets.isNullOrEmpty()) leaving = true else actions.onFinish() },
                onOpenSheet = openSheet,
            )
        },
    )
    SessionLayout(parts, modifier)
    if (logging) LogSetSheet(state, clocks, actions, onDismiss = { logging = false })
    if (reading) ExercisePageSheet(state, actions, onDismiss = { reading = false })
    if (picking) ExercisePickerSheet(state, actions, onDismiss = { picking = false })
    if (pickingDay) ProgramDaySheet(state, onPick = actions.onPickDay, onDismiss = { pickingDay = false })
    PlanSheets(state, actions, planning, changing, onPlanning = { planning = it }, onChanging = { changing = it })
    EndDialogs(leaving, discarding, actions.onDiscard, { leaving = it }, { discarding = it })
}

@Composable
private fun SessionPlanContent(
    state: SessionUiState,
    actions: SessionActions,
    sets: SetActions,
    onRead: () -> Unit,
    onPick: () -> Unit,
    onPickDay: () -> Unit,
    onChange: (Int) -> Unit,
    onPlan: () -> Unit,
    onLog: () -> Unit,
    celebrating: Long?,
) {
    val opening = actions.copy(
        onOpenExercise = {
            actions.onOpenExercise(it)
            onRead()
        },
    )
    // Not while a set logged is being changed: the draft is that set until it is saved or left.
    if (state.free && state.editing == null) {
        DraftSetCard(
            state,
            opening,
            onChange = onPick,
            onTimeIt = {
                actions.onTimeHold()
                onLog()
            },
            celebrating = celebrating,
        )
        // A day of any program, to do as this session instead of composing each set.
        ListCard(Modifier.padding(top = Spacing.space8)) {
            LinkRow(
                stringResource(Res.string.next_program_day),
                stringResource(Res.string.next_program_day_hint),
                onClick = onPickDay,
            )
        }
    } else if (!state.free) {
        state.easier?.let { EasierCard(it, actions.onUseEasier) }
        UpNextCard(
            state,
            opening,
            onChange = onChange,
            onPlan = onPlan,
            onTimeIt = {
                actions.onTimePlannedHold()
                onLog()
            },
            celebrating = celebrating,
        )
    }
    LoggedSets(state.session?.sets.orEmpty(), state.recordSetIds, sets)
}

/**
 * The two ways out of a session that drop it: Finish with nothing logged ([leaving], asked in Back's words) and
 * × ([discarding]). Each confirms with [onDiscard].
 */
@Composable
private fun EndDialogs(
    leaving: Boolean,
    discarding: Boolean,
    onDiscard: () -> Unit,
    onLeaving: (Boolean) -> Unit,
    onDiscarding: (Boolean) -> Unit,
) {
    if (leaving) {
        LeaveDialog(
            running = null,
            hasSets = false,
            onLeave = {
                onLeaving(false)
                onDiscard()
            },
            onDismiss = { onLeaving(false) },
        )
    }
    if (discarding) {
        ConfirmDialog(
            title = stringResource(Res.string.session_discard_title),
            text = stringResource(Res.string.session_discard_text),
            confirm = stringResource(Res.string.session_discard),
            dismiss = stringResource(Res.string.common_cancel),
            onConfirm = {
                onDiscarding(false)
                onDiscard()
            },
            onDismiss = { onDiscarding(false) },
            destructive = true,
        )
    }
}

/**
 * The page of the exercise up next, over the session: no Edit, as its sets and today's plan hold it as it is,
 * and no past session to open from here.
 */
@Composable
private fun ExercisePageSheet(state: SessionUiState, actions: SessionActions, onDismiss: () -> Unit) {
    val detail = state.detail ?: return
    ExerciseDetailSheet(
        detail,
        onOpenSession = null,
        onDismiss = {
            onDismiss()
            actions.onOpenExercise(null)
        },
        editable = false,
    )
}

/**
 * A set logged while the screen is up that beat a record is announced once, under the thumb (two beats,
 * [Motion.RECORD_BEAT_GAP_MILLIS] apart, which no other moment has), in a notice, and by the next lift's figure
 * ([onCelebrate] takes the set's id with the first beat and null once the figure's gesture is over, so a card that
 * comes in on the moment joins it and one that comes in later does not); a record already standing when the
 * screen opened (a session resumed) is not.
 */
@Composable
private fun AnnounceRecord(state: SessionUiState, notices: Notices, onCelebrate: (Long?) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val latest = state.latestRecord
    val recordText = latest?.let { stringResource(Res.string.notice_record, setLine(it)) }
    val seenAtOpen = remember { latest?.id }
    val celebrate by rememberUpdatedState(onCelebrate)
    LaunchedEffect(latest?.id) {
        if (latest != null && latest.id != seenAtOpen && recordText != null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            notices.show(recordText)
            celebrate(latest.id)
            delay(Motion.RECORD_BEAT_GAP_MILLIS)
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            delay(Celebration.MILLIS.toLong())
            celebrate(null)
        }
    }
}

/** A session of swings then goblet squats, a set every two minutes, for the preview. */
private val previewSets = List(6) { index ->
    val exercise = if (index < 4) BuiltInExercise.TwoHandSwing else BuiltInExercise.GobletSquat
    SetLog.of(index + 1L, exercise, kg = if (index < 4) 24.0 else 16.0, reps = 10, side = null, index * 120_000L)
}

@PreviewFormFactors
@Composable
private fun SessionScreenPreview() {
    val state = SessionUiState(
        session = LiftSession("s", startedAtEpochMillis = 0, finishedAtEpochMillis = null, "Swings", previewSets),
        ownsBells = true,
        bells = listOf(16.0, 24.0),
        draft = SetDraft(exercise = BuiltInExercise.GobletSquat, bellKg = 16.0),
        free = true,
        draftLastTime = previewSets.last(),
    )
    val clocks = SessionClocks(elapsed = "14:32", sinceLast = "0:38", rest = RestState(90, 52, 0.42f))
    IntervalTheme {
        SessionScreen(state, { clocks }, SessionActions())
    }
}
