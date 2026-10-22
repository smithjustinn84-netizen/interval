package dev.justinsmith.bells.session

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.justinsmith.bells.domain.Doubles
import dev.justinsmith.bells.domain.SetLog
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.domain.formatKg
import dev.justinsmith.bells.domain.kettlebellLifts
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.common_cancel
import dev.justinsmith.bells.resources.common_joined
import dev.justinsmith.bells.resources.emom_fewer_minutes
import dev.justinsmith.bells.resources.emom_minutes
import dev.justinsmith.bells.resources.emom_more_minutes
import dev.justinsmith.bells.resources.emom_start
import dev.justinsmith.bells.resources.kg_value
import dev.justinsmith.bells.resources.lift_double
import dev.justinsmith.bells.resources.log_bell
import dev.justinsmith.bells.resources.log_exercise
import dev.justinsmith.bells.resources.log_fewer_reps
import dev.justinsmith.bells.resources.log_first_side
import dev.justinsmith.bells.resources.log_kind_emom
import dev.justinsmith.bells.resources.log_kind_set
import dev.justinsmith.bells.resources.log_more_reps
import dev.justinsmith.bells.resources.log_no_bells
import dev.justinsmith.bells.resources.log_reps
import dev.justinsmith.bells.resources.log_reps_each_minute
import dev.justinsmith.bells.resources.log_side
import dev.justinsmith.bells.resources.log_title
import dev.justinsmith.bells.resources.next_day_done
import dev.justinsmith.bells.resources.next_emom
import dev.justinsmith.bells.resources.next_log
import dev.justinsmith.bells.resources.next_progress
import dev.justinsmith.bells.resources.next_title
import dev.justinsmith.bells.resources.session_discard
import dev.justinsmith.bells.resources.session_discard_text
import dev.justinsmith.bells.resources.session_discard_title
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
import dev.justinsmith.bells.resources.set_line
import dev.justinsmith.bells.resources.set_line_double
import dev.justinsmith.bells.resources.set_line_side
import dev.justinsmith.bells.ui.ChoiceRow
import dev.justinsmith.bells.ui.StatTile
import dev.justinsmith.bells.ui.exerciseName
import dev.justinsmith.bells.ui.liftName
import dev.justinsmith.bells.ui.sessionTitle
import dev.justinsmith.bells.ui.sideName
import dev.justinsmith.interval.platform.KeepScreenOn
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.RepeatButton
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.SegmentedPicker
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TitleBar
import dev.justinsmith.interval.ui.ToggleRow
import dev.justinsmith.interval.ui.surfaceButton
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

@Composable
fun SessionRoute(onExit: () -> Unit) {
    val scope = currentKoinScope()
    val viewModel = viewModel { SessionViewModel(scope.get(), scope.get(), scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val finished = state.finished
    if (finished != null) {
        SummaryScreen(finished, onDone = onExit)
        return
    }
    val emom = state.emom
    val emomState = state.emomState
    LeaveGuard(
        emomRunning = emom != null,
        hasSets = !state.session?.sets.isNullOrEmpty(),
        onLeave = {
            viewModel.stopEmom()
            onExit()
        },
    )
    if (emom != null && emomState != null) {
        EmomScreen(emom, emomState, onFinishSet = viewModel::emomSetDone, onStop = viewModel::stopEmom)
        return
    }
    val title = sessionTitle(state.session?.exercises.orEmpty())
    SessionScreen(
        state = state,
        actions = SessionActions(
            onDraft = viewModel::updateDraft,
            onLog = viewModel::logSet,
            onLogNext = viewModel::logNext,
            onStartPlannedEmom = viewModel::startPlannedEmom,
            onStartEmom = viewModel::startDraftEmom,
            onRemoveSet = viewModel::removeSet,
            onRestoreSet = viewModel::restoreSet,
            onFinish = {
                if (state.session?.sets.isNullOrEmpty()) {
                    viewModel.discard()
                    onExit()
                } else {
                    viewModel.finish(title)
                }
            },
            onDiscard = {
                viewModel.discard()
                onExit()
            },
        ),
    )
}

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
    val onLog: () -> Unit = {},
    val onLogNext: () -> Unit = {},
    val onStartPlannedEmom: () -> Unit = {},
    val onStartEmom: () -> Unit = {},
    val onRemoveSet: (Long) -> Unit = {},
    val onRestoreSet: (SetLog) -> Unit = {},
    val onFinish: () -> Unit = {},
    val onDiscard: () -> Unit = {},
)

/** The session in progress: its time and weight moved, its sets by lift, and Log set. */
@Composable
fun SessionScreen(state: SessionUiState, actions: SessionActions, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    var logging by rememberSaveable { mutableStateOf(false) }
    var discarding by rememberSaveable { mutableStateOf(false) }
    // The set just removed, offered back for a few seconds.
    var removed by remember { mutableStateOf<SetLog?>(null) }
    LaunchedEffect(removed) {
        if (removed != null) {
            delay(Motion.UNDO_MILLIS)
            removed = null
        }
    }
    KeepScreenOn()
    Box(modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Sizes.content).fillMaxSize().safeDrawingPadding()) {
            TitleBar(
                stringResource(Res.string.session_title),
                navigation = {
                    IconAction(IntervalIcons.Close, stringResource(Res.string.session_discard), { discarding = true })
                },
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.space16),
                verticalArrangement = Arrangement.spacedBy(Spacing.space8),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                    StatTile(stringResource(Res.string.session_time), state.elapsed, Modifier.weight(1f))
                    StatTile(
                        stringResource(Res.string.session_moved),
                        stringResource(Res.string.kg_value, formatKg(state.session?.tonnageKg ?: 0.0)),
                        Modifier.weight(1f),
                    )
                }
                state.sinceLast?.let {
                    StatTile(stringResource(Res.string.session_since_last), it, Modifier.fillMaxWidth())
                }
                UpNextCard(state, actions)
                LoggedSets(state, onRemove = { set ->
                    removed = set
                    actions.onRemoveSet(set.id)
                })
            }
            removed?.let { set ->
                UndoBar(
                    stringResource(
                        Res.string.session_set_removed,
                        setLine(set.bellKg, set.reps, set.side, set.double),
                    ),
                    onUndo = {
                        removed = null
                        actions.onRestoreSet(set)
                    },
                    Modifier.padding(start = Spacing.space16, end = Spacing.space16, top = Spacing.space8),
                )
            }
            Row(
                Modifier.padding(Spacing.space16),
                horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
            ) {
                SecondaryButton(stringResource(Res.string.session_finish), actions.onFinish, Modifier.weight(1f))
                AccentButton(stringResource(Res.string.session_log), { logging = true }, Modifier.weight(2f))
            }
        }
    }
    if (logging) LogSetSheet(state, actions, onDismiss = { logging = false })
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

/** The sets logged so far, by lift; before the first, and with no plan, how to log one. */
@Composable
private fun LoggedSets(state: SessionUiState, onRemove: (SetLog) -> Unit) {
    val sets = state.session?.sets.orEmpty()
    if (sets.isEmpty() && state.upNext == null) {
        Text(
            stringResource(Res.string.session_empty),
            style = IntervalTheme.type.body,
            color = IntervalTheme.palette.textDim,
            modifier = Modifier.padding(vertical = Spacing.space16),
        )
    }
    sets.groupBy { it.exercise to it.double }.forEach { (lift, done) ->
        GroupLabel(liftName(lift.first, lift.second))
        ListCard {
            done.forEachIndexed { i, set ->
                if (i > 0) RowDivider()
                SetRow(set, onRemove = { onRemove(set) })
            }
        }
    }
}

@Composable
private fun SetRow(set: SetLog, onRemove: () -> Unit) {
    val line = setLine(set.bellKg, set.reps, set.side, set.double)
    Row(
        Modifier.fillMaxWidth().height(Sizes.listRow).padding(start = Spacing.space16, end = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(line, style = IntervalTheme.type.label, color = IntervalTheme.palette.text, modifier = Modifier.weight(1f))
        IconAction(IntervalIcons.Close, stringResource(Res.string.session_remove_set_cd, line), onRemove)
    }
}

/** A removal just made, and Undo: read out as it appears. */
@Composable
private fun UndoBar(text: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .clip(Corners.radius12)
            .background(palette.surfaceVariant)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = Spacing.space16, end = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = IntervalTheme.type.label, color = palette.text, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .heightIn(min = Sizes.pillHeight)
                .surfaceButton(interaction, Corners.radius12, Color.Transparent, onUndo)
                .padding(horizontal = Spacing.space16),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(Res.string.session_undo), style = IntervalTheme.type.control, color = palette.text)
        }
    }
}

/** A set in words: "24 kg × 10", "20 kg × 5 · Left", or with two bells "2 × 16 kg × 5". */
@Composable
fun setLine(bellKg: Double, reps: Int, side: Side?, double: Boolean = false): String = when {
    double -> stringResource(Res.string.set_line_double, formatKg(bellKg), reps)
    side == null -> stringResource(Res.string.set_line, formatKg(bellKg), reps)
    else -> stringResource(Res.string.set_line_side, formatKg(bellKg), reps, sideName(side))
}

/** What the Log sheet adds: one set now, or an EMOM of the set every minute. */
private enum class LogKind { Set, Emom }

/**
 * Pick a set or an EMOM, then the lift, the bell (or two) and the reps; for a set, the side of a one-sided
 * lift, for an EMOM its minutes and first side. Then log the set, or start the EMOM.
 */
@Composable
private fun LogSetSheet(state: SessionUiState, actions: SessionActions, onDismiss: () -> Unit) {
    val draft = state.draft
    val palette = IntervalTheme.palette
    // Saved by name: an enum is not saveable on every platform.
    var kindName by rememberSaveable { mutableStateOf(LogKind.Set.name) }
    val kind = LogKind.valueOf(kindName)
    IntervalSheet(stringResource(Res.string.log_title), onDismiss = onDismiss) { hide ->
        Column(
            Modifier.padding(horizontal = Spacing.space20, vertical = Spacing.space8),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            SegmentedPicker(
                LogKind.entries,
                kind,
                label = {
                    stringResource(if (it == LogKind.Set) Res.string.log_kind_set else Res.string.log_kind_emom)
                },
                onSelect = { kindName = it.name },
                modifier = Modifier.padding(bottom = Spacing.space8),
            )
            GroupLabel(stringResource(Res.string.log_exercise))
            ChoiceRow(kettlebellLifts, draft.exercise, {
                exerciseName(it)
            }, { e -> actions.onDraft { it.copy(exercise = e) } })
            GroupLabel(stringResource(Res.string.log_bell))
            ChoiceRow(state.bells, draft.bellKg, { formatKg(it) }, { kg -> actions.onDraft { it.copy(bellKg = kg) } })
            if (!state.ownsBells) {
                Text(stringResource(Res.string.log_no_bells), style = IntervalTheme.type.meta, color = palette.textDim)
            }
            if (draft.exercise.doubles == Doubles.Optional) {
                ListCard {
                    ToggleRow(
                        stringResource(Res.string.lift_double),
                        null,
                        checked = draft.double,
                        onToggle = { actions.onDraft { it.copy(double = !it.double) } },
                    )
                }
            }
            GroupLabel(
                stringResource(if (kind == LogKind.Set) Res.string.log_reps else Res.string.log_reps_each_minute),
            )
            CountStepper(
                draft.reps,
                fewer = stringResource(Res.string.log_fewer_reps),
                more = stringResource(Res.string.log_more_reps),
                max = SetDraft.MAX_REPS,
                onStep = { step -> actions.onDraft { it.copy(reps = it.reps + step) } },
            )
            if (kind == LogKind.Emom) {
                GroupLabel(stringResource(Res.string.emom_minutes))
                CountStepper(
                    draft.minutes,
                    fewer = stringResource(Res.string.emom_fewer_minutes),
                    more = stringResource(Res.string.emom_more_minutes),
                    max = SetDraft.MAX_MINUTES,
                    onStep = { step -> actions.onDraft { it.copy(minutes = it.minutes + step) } },
                )
            }
            if (draft.exercise.sided(draft.doubled)) {
                GroupLabel(
                    stringResource(if (kind == LogKind.Set) Res.string.log_side else Res.string.log_first_side),
                )
                ChoiceRow(Side.entries, draft.side, {
                    sideName(it)
                }, { side -> actions.onDraft { it.copy(side = side) } })
            }
            AccentButton(
                stringResource(if (kind == LogKind.Set) Res.string.session_log else Res.string.emom_start),
                onClick = {
                    if (kind == LogKind.Set) actions.onLog() else actions.onStartEmom()
                    hide()
                },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.space16, bottom = Spacing.space8),
            )
        }
    }
}

/** A count from 1 to [max], large, with − and + that repeat while held. */
@Composable
private fun CountStepper(value: Int, fewer: String, more: String, max: Int, onStep: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Text(
            value.toString(),
            style = IntervalTheme.type.display,
            color = IntervalTheme.palette.text,
            modifier = Modifier.weight(1f),
        )
        RepeatButton("−", fewer, enabled = value > 1, onStep = { onStep(-1) })
        RepeatButton("+", more, enabled = value < max, onStep = { onStep(1) })
    }
}

/** In a program day: the next planned set, with its bell, to log in one tap or to start its EMOM. */
@Composable
private fun UpNextCard(state: SessionUiState, actions: SessionActions) {
    val palette = IntervalTheme.palette
    val next = state.upNext
    if (next == null) {
        if (state.dayDone) {
            Text(
                stringResource(Res.string.next_day_done),
                style = IntervalTheme.type.body,
                color = palette.textDim,
                modifier = Modifier.padding(vertical = Spacing.space8),
            )
        }
        return
    }
    ListCard(Modifier.padding(top = Spacing.space8)) {
        Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            Text(
                stringResource(
                    Res.string.common_joined,
                    stringResource(Res.string.next_title),
                    stringResource(Res.string.next_progress, next.number, next.total),
                ),
                style = IntervalTheme.type.caption,
                color = palette.textDim,
            )
            Text(liftName(next.set.exercise, next.set.double), style = IntervalTheme.type.section, color = palette.text)
            Text(
                setLine(next.bellKg, next.set.reps, next.set.side, next.set.double),
                style = IntervalTheme.type.value,
                color = palette.text,
            )
            if (next.emomMinutes != null) {
                AccentButton(
                    stringResource(Res.string.next_emom),
                    actions.onStartPlannedEmom,
                    Modifier.fillMaxWidth().padding(top = Spacing.space8),
                )
            } else {
                AccentButton(
                    stringResource(Res.string.next_log),
                    actions.onLogNext,
                    Modifier.fillMaxWidth().padding(top = Spacing.space8),
                )
            }
        }
    }
}
