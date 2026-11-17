package dev.justinsmith.interval.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.FlowStore
import dev.justinsmith.interval.data.HistoryStore
import dev.justinsmith.interval.data.PresetStore
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.model.SnatchCount
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.timer.Flow
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.asksSnatches
import dev.justinsmith.interval.ui.SnatchAsk
import dev.justinsmith.lift.data.LiftActivity
import dev.justinsmith.lift.domain.IntervalSnatches
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.intervalSets
import dev.justinsmith.lift.domain.sets
import dev.justinsmith.lift.domain.wantsHandCare
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Workout page's view of the [WorkoutSession], which runs the workout: it opens the one the route's [id]
 * names, shows it with the preset its shape matches, and hands the screen's actions on. The workout outlives
 * this ViewModel, which goes with the screen when Android destroys it in the background; it ends only when the
 * page leaves the back stack.
 */
class WorkoutViewModel(
    /** The route's: the workout this page shows, and the saved progress it picks up after the process died. */
    private val id: Long,
    /** The route's: the flow a new workout runs, null for the Timer's own. */
    flow: String?,
    private val session: WorkoutSession,
    private val presetStore: PresetStore,
    private val flowStore: FlowStore,
    private val historyStore: HistoryStore,
    private val lift: LiftActivity,
    private val timestamps: TimestampFormatter,
) : ViewModel() {
    init {
        session.open(id, flow)
    }

    // Followed rather than read once: a preset saved from the summary lands later, and the summary must then
    // name it. Matched once per change of either, not only on a tick.
    val state: StateFlow<WorkoutUiState> = combine(
        session.state.filterNotNull().filter { it.id == id },
        presetStore.presets,
        flowStore.flows,
        historyStore.history,
        combine(lift.bells, lift.sessions, lift.active, ::LiftSide),
    ) { run, presets, flows, history, lifted -> run.shown(presets, flows, history, lifted) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            checkNotNull(session.state.value).shown(
                presetStore.presets.value,
                flowStore.flows.value,
                historyStore.history.value,
                LiftSide(lift.bells.value, lift.sessions.value, lift.active.value),
            ),
        )

    /** What the summary reads of Lift: the [bells] owned, and every set lifted, finished or under way. */
    private class LiftSide(val bells: List<Double>, sessions: List<LiftSession>, active: LiftSession?) {
        val sets: List<SetLog> = sessions.flatMap { it.sets } + active?.sets.orEmpty()
    }

    /**
     * How far through its interval the workout is, 0 to 1, for the ring; 0 once it is over. Apart from [state],
     * which changes once a second, since this moves every tick.
     */
    val progress: StateFlow<Float> = session.state.filterNotNull().filter { it.id == id }
        .map { it.ringProgress }
        .stateIn(viewModelScope, SharingStarted.Eagerly, checkNotNull(session.state.value).ringProgress)

    private val WorkoutRun.ringProgress: Float get() = if (ended || engine.isDone) 0f else engine.progress

    /**
     * [WorkoutRun.toUiState], with the snatch question once it is over and the workout is one of snatches, and the
     * hands' care once its count and the day's other snatches come to enough.
     */
    private fun WorkoutRun.shown(
        presets: List<Preset>,
        flows: List<Flow>,
        history: List<HistoryEntry>,
        lifted: LiftSide,
    ): WorkoutUiState {
        val shown = toUiState(presets, flows)
        if (entryUid == null || !presets.asksSnatches(config)) return shown
        // Its own entry, in History by now, holds this count or none, so the last one said stands either way.
        return shown.copy(
            snatches = SnatchAsk.of(snatches, history, lifted.bells),
            handCare = handCare(history, lifted.sets),
        )
    }

    /**
     * Whether the snatches counted, with the day's others (Lift's sets, and the other interval workouts counted),
     * want the hands looked after, as Lift's summary says of a session: see [wantsHandCare]. Read as Lift reads
     * them, a set a work interval done, from its own entry in History (recorded from [WorkoutRun.startedAt]).
     */
    private fun WorkoutRun.handCare(history: List<HistoryEntry>, lifted: List<SetLog>): Boolean {
        val count = snatches ?: return false
        val (own, others) = history.partition { it.startedAtEpochMillis == startedAt }
        val entry = own.firstOrNull() ?: return false
        val done = intervalSets(listOf(IntervalSnatches(count.perSet, count.bellKg, entry.workEnds(), null))).sets
        val alongside = intervalSets(
            others.mapNotNull { other ->
                other.snatches?.let { IntervalSnatches(it.perSet, it.bellKg, other.workEnds(), null) }
            },
        ).sets
        val today = timestamps.dayNumber(entry.finishedAtEpochMillis)
        return wantsHandCare(done, alongside + lifted, today, timestamps::dayNumber)
    }

    fun togglePause() = session.togglePause()

    fun skip() = session.skip()

    fun back() = session.back()

    fun addTime() = session.addTime()

    /**
     * Stops before the last round: records what was done and shows the summary. Without a finished round there is
     * nothing worth keeping, so the screen just leaves instead of calling this.
     */
    fun endEarly() = session.end()

    fun restart() = session.restart()

    /**
     * Saves [preset], named and maybe reshaped in the summary's editor, as a new one of the user's own,
     * and puts it on the Timer, as saving from the Timer or Presets does. The summary names it once the
     * save lands.
     */
    fun saveAsPreset(preset: Preset) {
        presetStore.saveAndApplyPreset(preset)
    }

    /** How hard the finished workout felt, from the summary; kept in History with it at once. */
    fun setEffort(effort: PerceivedEffort?) = session.reflect(effort, session.state.value?.note.orEmpty())

    /** The summary's note as typed; kept in History with the workout at once, trimmed. */
    fun setNote(note: String) = session.reflect(session.state.value?.effort, note)

    /** About how many snatches a set the finished workout held, from the summary; null takes the count away. */
    fun countSnatches(count: SnatchCount?) = session.countSnatches(count)

    /**
     * Reads the finished workout's heart rate for the summary, as it comes into view: each time, until one is
     * found, as a watch may sync after the workout ends. Nothing before it is finished.
     */
    fun readHeartRate() {
        viewModelScope.launch { session.readHeartRate() }
    }

    /** The summary's effort and note in words, for the health store's note: see [WorkoutSession.describeForHealth]. */
    fun describeForHealth(notes: String?) = session.describeForHealth(notes)
}
