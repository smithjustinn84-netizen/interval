package dev.justinsmith.lift.session

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.justinsmith.interval.platform.Sharer
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.HeartBeatBadge
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.RingZone
import dev.justinsmith.interval.ui.fadeThrough
import dev.justinsmith.interval.ui.rememberRingZone
import dev.justinsmith.lift.data.ExerciseFilterStore
import dev.justinsmith.lift.data.SetStore
import dev.justinsmith.lift.domain.testOf
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.session_health_title
import dev.justinsmith.lift.session.resources.session_leave
import dev.justinsmith.lift.session.resources.session_leave_density_text
import dev.justinsmith.lift.session.resources.session_leave_emom_text
import dev.justinsmith.lift.session.resources.session_leave_empty_text
import dev.justinsmith.lift.session.resources.session_leave_text
import dev.justinsmith.lift.session.resources.session_leave_title
import dev.justinsmith.lift.session.resources.session_stay
import dev.justinsmith.lift.ui.programName
import dev.justinsmith.lift.ui.sessionTitle
import dev.justinsmith.lift.ui.testName
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SessionRoute(onExit: () -> Unit, onRecap: () -> Unit = onExit) {
    val viewModel = koinViewModel<SessionViewModel>()
    // A page a killed process left on a session since finished or dropped has nothing to go on with: it closes.
    if (viewModel.sessionGone) {
        val exit by rememberUpdatedState(onExit)
        LaunchedEffect(Unit) { exit() }
        return
    }
    val timers = koinInject<SessionTimers>()
    val sets = koinInject<SetStore>()
    val filters = koinInject<ExerciseFilterStore>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The clocks move every tick: read where they are drawn, so the rest of the screen can skip.
    val clocks = viewModel.clocks.collectAsStateWithLifecycle()
    val readClocks = remember(clocks) { { clocks.value } }
    val finished = state.finished
    // The summary's heart rate, each time it comes into view without one: a watch may sync after the session ends.
    val unread = finished != null && finished.session.averageBpm == null
    LifecycleStartEffect(unread) {
        if (unread) viewModel.readHeartRate()
        onStopOrDispose {}
    }
    if (finished == null) {
        LeaveGuard(
            running = state.clockStopping,
            hasSets = !state.session?.sets.isNullOrEmpty(),
            onLeave = {
                viewModel.leave()
                onExit()
            },
            onDiscard = {
                viewModel.discard()
                onExit()
            },
        )
    }
    // A test's session is headed by the test, in the log and in health: its one day needs no number.
    val test = testOf(state.programDay?.program?.key)
    val title = test?.let { testName(it.id) } ?: sessionTitle(state.session?.exercises.orEmpty())
    val healthTitle = state.programDay?.let {
        if (test != null) title else stringResource(Res.string.session_health_title, programName(it.program), it.number)
    }
    // One set of actions for as long as nothing they close over changes: the state changes every tick while a
    // rest or a hold runs, and a child that takes the actions alone can then skip.
    val actions =
        remember(viewModel, title, healthTitle, onExit) {
            sessionActions(ActionTargets(viewModel, timers, sets, filters), title, healthTitle, onExit)
        }
    // Computed, so the clocks' ticks reach this page only when the face up changes or what it says does.
    val page by remember { computedStateOf(structuralEqualityPolicy()) { sessionPage(state, clocks.value) } }
    val zone = rememberTimerZone(readClocks)
    // The session, its EMOM or density block, and its summary take one another's place with a fade through, as
    // pages do.
    AnimatedContent(
        targetState = page,
        transitionSpec = { fadeThrough(Motion.PAGE_MILLIS) },
        contentKey = { it::class },
        label = "sessionPage",
    ) { shown ->
        when (shown) {
            // A program's last day closes onto the program's recap.
            is SessionPage.Summary -> SessionSummary(
                shown.finished,
                viewModel.summary,
                onDone = if (shown.finished.lastDayOf != null) onRecap else onExit,
            )

            is SessionPage.Emom -> EmomScreen(
                shown.run,
                shown.state,
                onFinishSet = timers::emomSetDone,
                onStop = timers::stopEmom,
                progress = remember(clocks) { heldShare { clocks.value.emomState?.minuteProgress } },
                heart = { TimerHeart(readClocks) },
                zone = zone,
            )

            is SessionPage.Density -> DensityScreen(
                shown.run,
                shown.state,
                onFinishRound = timers::densityRoundDone,
                onStop = timers::stopDensity,
                progress = remember(clocks) { heldShare { clocks.value.densityState?.progress } },
                heart = { TimerHeart(readClocks) },
                zone = zone,
            )

            SessionPage.Logging -> SessionScreen(state, readClocks, actions)
        }
    }
}

/** What Leave says of the clock it stops: an EMOM's or a density block's; null with neither running. */
private val SessionUiState.clockStopping: StringResource?
    get() = when {
        emom != null -> Res.string.session_leave_emom_text
        density != null -> Res.string.session_leave_density_text
        else -> null
    }

/** What the Lifting page's actions act on: the session, its timers, its sets and the picker's filters. */
private class ActionTargets(
    val viewModel: SessionViewModel,
    val timers: SessionTimers,
    val sets: SetStore,
    val filters: ExerciseFilterStore,
)

private fun sessionActions(parts: ActionTargets, title: String, healthTitle: String?, onExit: () -> Unit) =
    with(parts) {
        SessionActions(
            onDraft = viewModel.draft::update,
            onUseEasier = viewModel.easierDay::use,
            onFilters = filters::update,
            onAddExercise = viewModel.draft::expectNewExercise,
            onLog = viewModel::logSet,
            onLogNext = viewModel::logNext,
            onOpenLog = viewModel::openLogSheet,
            onStepLoad = viewModel::stepNextLoad,
            onStepDraftLoad = viewModel.draft::stepLoad,
            onOpenExercise = viewModel::openExercise,
            onStartPlannedClock = viewModel::startPlannedClock,
            onTimePlannedHold = viewModel::startPlannedHold,
            onStartEmom = viewModel::startDraftEmom,
            onTimeHold = viewModel::startHold,
            onStopHold = timers::stopHold,
            onCancelHold = timers::cancelHold,
            onRemoveSet = sets::removeSet,
            onRestoreSet = sets::restoreSet,
            onEditSet = viewModel.draft::edit,
            onSaveEdit = viewModel::saveEdit,
            onCancelEdit = viewModel.draft::endEdit,
            onChangeBlock = viewModel.plan::changeBlock,
            onSwapBlock = viewModel.plan::swapBlock,
            onSkipBlock = viewModel.plan::skipBlock,
            onUnskipBlock = viewModel.plan::unskipBlock,
            onPairBlock = viewModel.plan::pairBlock,
            onStepRest = viewModel::stepRest,
            onSkipRest = timers::skipRest,
            onLogPartRound = viewModel::logPartRound,
            onPickDay = viewModel.plan::pickDay,
            // The screen asks first when nothing is logged; should one slip through, it is thrown away and the page
            // closes.
            onFinish = { if (!viewModel.finish(title, healthTitle)) onExit() },
            onDiscard = {
                viewModel.discard()
                onExit()
            },
        )
    }

/**
 * Back, or the back gesture, asks before leaving a session: with sets logged or an EMOM or a density block running,
 * the sets stay, for Resume session on Lift, but the clock running can't ([running] the words that say so). With
 * nothing logged there is nothing to keep, and Leave drops the session, as × → Discard does: a session with no sets is
 * never kept.
 */
@Composable
private fun LeaveGuard(running: StringResource?, hasSets: Boolean, onLeave: () -> Unit, onDiscard: () -> Unit) {
    var asking by rememberSaveable { mutableStateOf(false) }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        onBackCompleted = { asking = true },
    )
    if (asking) {
        LeaveDialog(
            running = running,
            hasSets = hasSets,
            onLeave = {
                asking = false
                if (hasSets) onLeave() else onDiscard()
            },
            onDismiss = { asking = false },
        )
    }
}

/**
 * "Leave the session?", Stay or Leave: what Leave does to the sets, or that there are none to keep. Back asks it
 * on every session, Finish on one with nothing logged.
 */
@Composable
internal fun LeaveDialog(running: StringResource?, hasSets: Boolean, onLeave: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(Res.string.session_leave_title),
        text = stringResource(
            when {
                !hasSets -> Res.string.session_leave_empty_text
                running != null -> running
                else -> Res.string.session_leave_text
            },
        ),
        confirm = stringResource(Res.string.session_leave),
        dismiss = stringResource(Res.string.session_stay),
        onConfirm = onLeave,
        onDismiss = onDismiss,
    )
}

/** The finished session's [SummaryScreen], bound to [summary] and the platform's share sheet. */
@Composable
private fun SessionSummary(finished: Finished, summary: FinishSession, onDone: () -> Unit) {
    val sharer = koinInject<Sharer>()
    SummaryScreen(
        finished,
        onDone = onDone,
        onKeep = summary::keepChanges,
        onShare = sharer::shareText,
        onSharePicture = { sharer.shareImage(SHARED_PICTURE, it) },
        reflection = SummaryReflection(summary::setEffort, summary::setNote, summary::describeForHealth),
    )
}

/** The live heart rate on a timer's page, the session's own being under it: the badge, on the workout's ground. */
@Composable
private fun TimerHeart(clocks: () -> SessionClocks) {
    val now = clocks()
    HeartBeatBadge(now.liveHeartRate, maxBpm = now.maxHeartRate)
}

/**
 * The live heart rate's zone as a timer's ring wears it, a bezel round its dial. Computed, so of the [clocks]'
 * ticks only a change in what the sensor reads reaches whoever holds it.
 */
@Composable
private fun rememberTimerZone(clocks: () -> SessionClocks): RingZone {
    val heart by remember(clocks) {
        computedStateOf(structuralEqualityPolicy()) { clocks().run { liveHeartRate to maxHeartRate } }
    }
    return rememberRingZone(heart.first, heart.second)
}
