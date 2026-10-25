package dev.justinsmith.bells.session

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.justinsmith.bells.data.ExerciseFilterStore
import dev.justinsmith.bells.domain.Block
import dev.justinsmith.bells.domain.EmomState
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.ExerciseFilters
import dev.justinsmith.bells.domain.SetLog
import dev.justinsmith.bells.domain.formatKg
import dev.justinsmith.bells.library.ExerciseDetailSheet
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.common_cancel
import dev.justinsmith.bells.resources.kg_value
import dev.justinsmith.bells.resources.notice_record
import dev.justinsmith.bells.resources.session_discard
import dev.justinsmith.bells.resources.session_discard_text
import dev.justinsmith.bells.resources.session_discard_title
import dev.justinsmith.bells.resources.session_edit_set_cd
import dev.justinsmith.bells.resources.session_empty
import dev.justinsmith.bells.resources.session_finish
import dev.justinsmith.bells.resources.session_leave
import dev.justinsmith.bells.resources.session_leave_emom_text
import dev.justinsmith.bells.resources.session_leave_text
import dev.justinsmith.bells.resources.session_leave_title
import dev.justinsmith.bells.resources.session_log
import dev.justinsmith.bells.resources.session_moved
import dev.justinsmith.bells.resources.session_remove_set_cd
import dev.justinsmith.bells.resources.session_set_removed
import dev.justinsmith.bells.resources.session_since_last
import dev.justinsmith.bells.resources.session_stay
import dev.justinsmith.bells.resources.session_time
import dev.justinsmith.bells.resources.session_title
import dev.justinsmith.bells.resources.session_undo
import dev.justinsmith.bells.ui.RecordTag
import dev.justinsmith.bells.ui.StatTile
import dev.justinsmith.bells.ui.liftName
import dev.justinsmith.bells.ui.sessionTitle
import dev.justinsmith.bells.ui.setLine
import dev.justinsmith.interval.platform.KeepScreenOn
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.NoticeAction
import dev.justinsmith.interval.ui.Notices
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TitleBar
import dev.justinsmith.interval.ui.enterRising
import dev.justinsmith.interval.ui.fadeThrough
import dev.justinsmith.interval.ui.isLandscape
import dev.justinsmith.interval.ui.rememberArrival
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

@Composable
fun SessionRoute(onExit: () -> Unit) {
    val scope = currentKoinScope()
    val filters = scope.get<ExerciseFilterStore>()
    val viewModel =
        viewModel { SessionViewModel(scope.get(), scope.get(), scope.get(), filters, scope.get(), scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val finished = state.finished
    val emom = state.emom
    val emomState = state.emomState
    if (finished == null) {
        LeaveGuard(
            emomRunning = emom != null,
            hasSets = !state.session?.sets.isNullOrEmpty(),
            onLeave = {
                viewModel.stopEmom()
                viewModel.cancelHold()
                onExit()
            },
        )
    }
    val title = sessionTitle(state.session?.exercises.orEmpty())
    val page = when {
        finished != null -> SessionPage.Summary(finished)
        emom != null && emomState != null -> SessionPage.Emom(emom, emomState)
        else -> SessionPage.Logging
    }
    // The session, its EMOM and its summary take one another's place with a fade through, as pages do.
    AnimatedContent(
        targetState = page,
        transitionSpec = { fadeThrough(Motion.PAGE_MILLIS) },
        contentKey = { it::class },
        label = "sessionPage",
    ) { shown ->
        when (shown) {
            is SessionPage.Summary -> SummaryScreen(shown.finished, onDone = onExit, onKeep = viewModel::keepChanges)

            is SessionPage.Emom -> EmomScreen(
                shown.run,
                shown.state,
                onFinishSet = viewModel::emomSetDone,
                onStop = viewModel::stopEmom,
            )

            SessionPage.Logging -> SessionScreen(
                state,
                sessionActions(viewModel, filters, title, onExit),
            )
        }
    }
}

/** Which of the Lifting page's faces is up. Each carries what it shows, so a face on its way out still has it. */
private sealed interface SessionPage {
    data class Summary(val finished: Finished) : SessionPage

    data class Emom(val run: EmomRun, val state: EmomState) : SessionPage

    data object Logging : SessionPage
}

private fun sessionActions(
    viewModel: SessionViewModel,
    filters: ExerciseFilterStore,
    title: String,
    onExit: () -> Unit,
): SessionActions = SessionActions(
    onDraft = viewModel::updateDraft,
    onFilters = filters::update,
    onAddExercise = viewModel::expectNewExercise,
    onLog = viewModel::logSet,
    onLogNext = viewModel::logNext,
    onOpenLog = viewModel::openLogSheet,
    onStepLoad = viewModel::stepNextLoad,
    onOpenExercise = viewModel::openExercise,
    onStartPlannedEmom = viewModel::startPlannedEmom,
    onStartEmom = viewModel::startDraftEmom,
    onTimeHold = viewModel::startHold,
    onStopHold = viewModel::stopHold,
    onCancelHold = viewModel::cancelHold,
    onRemoveSet = viewModel::removeSet,
    onRestoreSet = viewModel::restoreSet,
    onEditSet = viewModel::editSet,
    onSaveEdit = viewModel::saveEdit,
    onCancelEdit = viewModel::cancelEdit,
    onChangeBlock = viewModel::changeBlock,
    onSwapBlock = viewModel::swapBlock,
    onSkipBlock = viewModel::skipBlock,
    onUnskipBlock = viewModel::unskipBlock,
    // A session with nothing logged is thrown away, and with no summary to show, the page closes.
    onFinish = { if (!viewModel.finish(title)) onExit() },
    onDiscard = {
        viewModel.discard()
        onExit()
    },
)

/**
 * Back, or the back gesture, asks before leaving a session with sets logged or an EMOM running: the sets
 * stay, for Resume session on Lift, but the EMOM's clock can't. With neither, Back just leaves.
 */
@Composable
private fun LeaveGuard(emomRunning: Boolean, hasSets: Boolean, onLeave: () -> Unit) {
    var asking by rememberSaveable { mutableStateOf(false) }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = emomRunning || hasSets,
        onBackCompleted = { asking = true },
    )
    if (asking) {
        ConfirmDialog(
            title = stringResource(Res.string.session_leave_title),
            text = stringResource(
                if (emomRunning) Res.string.session_leave_emom_text else Res.string.session_leave_text,
            ),
            confirm = stringResource(Res.string.session_leave),
            dismiss = stringResource(Res.string.session_stay),
            onConfirm = {
                asking = false
                onLeave()
            },
            onDismiss = { asking = false },
        )
    }
}

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
    /** An exercise's page, by its key, opened; null closes it. */
    val onOpenExercise: (String?) -> Unit = {},
    val onStartPlannedEmom: () -> Unit = {},
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
    val onFinish: () -> Unit = {},
    val onDiscard: () -> Unit = {},
)

/** The session in progress: its time and weight moved, its sets by lift, and Log set. */
@Composable
fun SessionScreen(state: SessionUiState, actions: SessionActions, modifier: Modifier = Modifier) {
    var logging by rememberSaveable { mutableStateOf(false) }
    var discarding by rememberSaveable { mutableStateOf(false) }
    var planning by rememberSaveable { mutableStateOf(false) }
    // The exercise's page open over the session, from Up next.
    var reading by rememberSaveable { mutableStateOf(false) }
    // The block of today's plan open to change, -1 for none: a plain number saves on every platform.
    var changing by rememberSaveable { mutableIntStateOf(-1) }
    KeepScreenOn()
    val notices = LocalNotices.current
    val undo = stringResource(Res.string.session_undo)
    AnnounceRecord(state, notices)
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
            )
        },
        tiles = { wide -> SessionTiles(state, wide) },
        plan = {
            UpNextCard(
                state,
                actions.copy(
                    onOpenExercise = {
                        actions.onOpenExercise(it)
                        reading = true
                    },
                ),
                onChange = { changing = it },
                onPlan = { planning = true },
            )
            LoggedSets(state, sets)
        },
        buttons = {
            SessionButtons(
                onFinish = actions.onFinish,
                onLog = {
                    actions.onOpenLog()
                    logging = true
                },
            )
        },
    )
    SessionLayout(parts, modifier)
    if (logging) LogSetSheet(state, actions, onDismiss = { logging = false })
    if (reading) ExercisePageSheet(state, actions, onDismiss = { reading = false })
    PlanSheets(state, actions, planning, changing, onPlanning = { planning = it }, onChanging = { changing = it })
    if (discarding) {
        ConfirmDialog(
            title = stringResource(Res.string.session_discard_title),
            text = stringResource(Res.string.session_discard_text),
            confirm = stringResource(Res.string.session_discard),
            dismiss = stringResource(Res.string.common_cancel),
            onConfirm = {
                discarding = false
                actions.onDiscard()
            },
            onDismiss = { discarding = false },
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
 * A set logged while the screen is up that beat a record is announced once, under the thumb and in a
 * notice; a record already standing when the screen opened (a session resumed) is not.
 */
@Composable
private fun AnnounceRecord(state: SessionUiState, notices: Notices) {
    val haptics = LocalHapticFeedback.current
    val latest = state.latestRecord
    val recordText = latest?.let { stringResource(Res.string.notice_record, setLine(it)) }
    val seenAtOpen = remember { latest?.id }
    LaunchedEffect(latest?.id) {
        if (latest != null && latest.id != seenAtOpen && recordText != null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            notices.show(recordText)
        }
    }
}

/**
 * Whether a session's screens (the session, its summary) are upright on a window wider than a phone, as a tablet
 * held upright is: they keep one column, the width of the Timer's choices rather than a phone's. Two columns
 * there left the fixed one mostly empty between its numbers and its buttons.
 */
internal val BoxWithConstraintsScope.isWideUpright: Boolean
    get() = !isLandscape && maxWidth >= Sizes.setupColumnLandscape + Sizes.listColumn

/** The session screen's parts, which [SessionLayout] lays out in one column or two. */
private class SessionParts(
    val bar: @Composable () -> Unit,
    /** The session's numbers, in one row when [wide] and two otherwise. */
    val tiles: @Composable (wide: Boolean) -> Unit,
    val plan: @Composable ColumnScope.() -> Unit,
    val buttons: @Composable () -> Unit,
)

/**
 * Upright, one column: the title, then the clock, what is next and what is done scrolling, then Log set; on a
 * tablet ([isWideUpright]) the column widens and the numbers sit in one row. On its side, the title, the clock
 * and Log set on the left, and what is next and what is done on the right.
 */
@Composable
private fun SessionLayout(parts: SessionParts, modifier: Modifier = Modifier) {
    val scrolling = Arrangement.spacedBy(Spacing.space8)
    BoxWithConstraints(
        modifier.fillMaxSize().background(IntervalTheme.palette.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (isLandscape) {
            Row(Modifier.fillMaxSize().safeDrawingPadding(), horizontalArrangement = Arrangement.Center) {
                Column(Modifier.width(Sizes.setupColumnLandscape).fillMaxHeight()) {
                    parts.bar()
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.space16),
                    ) { parts.tiles(false) }
                    parts.buttons()
                }
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = Sizes.contentWide)
                        .fillMaxHeight()
                        .padding(end = Spacing.space16),
                ) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = Spacing.space16),
                        verticalArrangement = scrolling,
                        content = parts.plan,
                    )
                }
            }
        } else {
            val wide = isWideUpright
            Column(
                Modifier.widthIn(
                    max = if (wide) Sizes.contentWide else Sizes.content,
                ).fillMaxSize().safeDrawingPadding(),
            ) {
                parts.bar()
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.space16),
                    verticalArrangement = scrolling,
                ) {
                    parts.tiles(wide)
                    parts.plan(this)
                }
                parts.buttons()
            }
        }
    }
}

/** The session's time and weight moved, and how long since the last one: in one row when [wide]. */
@Composable
private fun SessionTiles(state: SessionUiState, wide: Boolean) {
    val moved = state.session?.tonnageKg ?: 0.0
    val time = @Composable { modifier: Modifier ->
        StatTile(stringResource(Res.string.session_time), state.elapsed, modifier)
    }
    val weight = @Composable { modifier: Modifier ->
        StatTile(
            stringResource(Res.string.session_moved),
            stringResource(Res.string.kg_value, formatKg(moved)),
            modifier,
            rank = moved.toInt(),
        )
    }
    val since = state.sinceLast
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            time(Modifier.weight(1f))
            weight(Modifier.weight(1f))
            if (since != null) StatTile(stringResource(Res.string.session_since_last), since, Modifier.weight(1f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                time(Modifier.weight(1f))
                weight(Modifier.weight(1f))
            }
            if (since != null) StatTile(stringResource(Res.string.session_since_last), since, Modifier.fillMaxWidth())
        }
    }
}

/** Finish, and Log set, which opens the sheet. */
@Composable
private fun SessionButtons(onFinish: () -> Unit, onLog: () -> Unit) {
    Row(
        Modifier.padding(Spacing.space16),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        SecondaryButton(stringResource(Res.string.session_finish), onFinish, Modifier.weight(1f))
        AccentButton(stringResource(Res.string.session_log), onLog, Modifier.weight(2f))
    }
}

/** What a logged set's row does: × removes it (the row words the notice), a tap opens it to change. */
private class SetActions(val onRemove: (SetLog, removedText: String) -> Unit, val onEdit: (SetLog) -> Unit)

/** The sets logged so far, by lift; before the first, and with no plan, how to log one. */
@Composable
private fun LoggedSets(state: SessionUiState, actions: SetActions) {
    val sets = state.session?.sets.orEmpty()
    if (sets.isEmpty() && state.upNext == null) {
        Text(
            stringResource(Res.string.session_empty),
            style = IntervalTheme.type.body,
            color = IntervalTheme.palette.textDim,
            modifier = Modifier.padding(vertical = Spacing.space16),
        )
    }
    // The sets on show when the screen opened stand still; one logged since rises into its card.
    val atOpen = remember { sets.map { it.id }.toSet() }
    sets.groupBy { it.exercise to it.double }.forEach { (lift, done) ->
        GroupLabel(liftName(lift.first, lift.second))
        ListCard(Modifier.animateContentSize()) {
            done.forEachIndexed { i, set ->
                key(set.id) {
                    AnimatedVisibility(rememberArrival(set.id !in atOpen), enter = enterRising()) {
                        Column {
                            if (i > 0) RowDivider()
                            SetRow(
                                set,
                                record = set.id in state.recordSetIds,
                                onRemove = { actions.onRemove(set, it) },
                                onEdit = { actions.onEdit(set) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A set logged: a tap opens it to change, × removes it; one that set a [record] is tagged. */
@Composable
private fun SetRow(set: SetLog, record: Boolean, onRemove: (removedText: String) -> Unit, onEdit: () -> Unit) {
    val line = setLine(set)
    val edit = stringResource(Res.string.session_edit_set_cd, line)
    val removed = stringResource(Res.string.session_set_removed, line)
    Row(
        Modifier
            .fillMaxWidth()
            .height(Sizes.listRow)
            .clickable(onClickLabel = edit, onClick = onEdit)
            .padding(start = Spacing.space16, end = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(line, style = IntervalTheme.type.label, color = IntervalTheme.palette.text, modifier = Modifier.weight(1f))
        if (record) RecordTag(Modifier.padding(end = Spacing.space8))
        IconAction(
            IntervalIcons.Close,
            stringResource(Res.string.session_remove_set_cd, line),
            onClick = { onRemove(removed) },
            haptic = HapticFeedbackType.SegmentTick,
        )
    }
}
