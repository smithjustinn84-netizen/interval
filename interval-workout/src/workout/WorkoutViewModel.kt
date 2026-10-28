package dev.justinsmith.interval.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.model.WorkoutProgress
import dev.justinsmith.interval.platform.BackgroundWorkout
import dev.justinsmith.interval.platform.CuePlayer
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.WorkoutControls
import dev.justinsmith.interval.platform.WorkoutStatus
import dev.justinsmith.interval.platform.playCue
import dev.justinsmith.interval.timer.Cue
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.MILLIS_PER_SECOND
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.Step
import dev.justinsmith.interval.timer.UpcomingChange
import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.timer.WorkoutState
import dev.justinsmith.interval.timer.matching
import dev.justinsmith.interval.timer.weekStanding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Runs one workout from the saved configuration: a coroutine ticker feeds elapsed time into
 * [WorkoutEngine], cues go to the [CuePlayer], and a finished workout is recorded in the history.
 *
 * The ticker reads [timeSource], which counts through the device's sleep, and gives the engine every
 * millisecond since its last tick, so a workout that ran on while the app was off screen is where it should
 * be when the screen comes back, through as many intervals as that takes ([WorkoutEngine.tick]). Off screen
 * it is kept going and in view by [background]. Its course is saved in the repository at each change
 * ([WorkoutProgress]), so a ViewModel built after the process was killed picks the workout up where it had
 * got to, or shows the summary of what ran, instead of starting again.
 */
// Every way a workout moves, side by side, and everything it reads, plays, keeps and drives, injected.
@Suppress("detekt.TooManyFunctions", "detekt.LongParameterList")
class WorkoutViewModel(
    /** The route's: the saved progress is this workout's only when the two match. */
    private val id: Long,
    private val repository: IntervalRepository,
    private val player: CuePlayer,
    private val bells: BellsRepository,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
    private val background: BackgroundWorkout,
    private val timeSource: TimeSource.WithComparableMarks,
) : ViewModel() {
    private val config: IntervalConfig

    /** Read once at start, like the configuration: a setting changed mid-workout applies next time. */
    private val keepScreenOn = repository.appSettings.value.keepScreenOn
    private val body = repository.appSettings.value.body
    private var engine: WorkoutState
    private var running = false
    private var ended = false

    /** Ended from off screen with nothing to keep: the screen leaves. */
    private var leaving = false

    /** Where the week stands with this workout in it, from the moment it is recorded; for the summary. */
    private var week: WeekStanding? = null

    /** Time the timer has run, pauses excluded: skips shorten it, added time lengthens it. */
    private var activeMillis = 0L
    private val activeSeconds: Int get() = activeMillis.roundedSeconds()

    /** Of [activeMillis], the time spent in work intervals, for the calorie estimate. */
    private var workMillis = 0L
    private val workSeconds: Int get() = workMillis.roundedSeconds()
    private var ticker: Job? = null

    /** True between [BackgroundWorkout.start] and [BackgroundWorkout.stop]: while there is a workout to keep. */
    private var kept = false

    /** The status last shown off screen, so [background] hears of a change, not of every tick. */
    private var shown: WorkoutStatus? = null

    // Followed rather than read on each publish: a preset saved from the summary lands later, and the
    // summary must then name it. Matched once per change, not on every tick.
    private var presets = repository.presets.value
    private var presetName: String?
    private val _state: MutableStateFlow<WorkoutUiState>

    private val controls = object : WorkoutControls {
        override fun togglePause() = this@WorkoutViewModel.togglePause()

        override fun end() = endOffScreen()

        // Up to a tick behind the clock: the platform's notifications land within a tenth of a second of the cue.
        override fun upcomingChanges(): List<UpcomingChange> =
            if (running && !over) WorkoutEngine.upcomingChanges(engine) else emptyList()
    }

    init {
        val saved = repository.workoutProgress.value?.takeIf { it.id == id }
        config = saved?.state?.config ?: repository.config.value
        presetName = presets.matching(config)?.name
        engine = saved?.state ?: WorkoutEngine.start(config).state
        _state = MutableStateFlow(uiState(running = saved?.running ?: true))
        // After _state: viewModelScope runs this at once, and its first value publishes.
        viewModelScope.launch {
            repository.presets.collect { all ->
                presets = all
                presetName = all.matching(config)?.name
                publish()
            }
        }
        if (saved != null) restore(saved) else begin()
    }

    val state: StateFlow<WorkoutUiState> = _state.asStateFlow()

    fun togglePause() {
        if (running) pause() else resume()
    }

    fun pause() {
        if (over || !running) return
        running = false
        stopTicker()
        publish()
        checkpoint()
    }

    fun resume() {
        if (over || ticker?.isActive == true) return
        running = true
        startTicker()
        publish()
        checkpoint()
    }

    /** Jumps to the next interval now. */
    fun skip() {
        if (over) return
        applyStep(WorkoutEngine.advance(engine))
        checkpoint()
    }

    fun back() {
        if (over) return
        engine = WorkoutEngine.back(engine)
        publish()
        checkpoint()
    }

    fun addTime() {
        if (over) return
        engine = WorkoutEngine.addTime(engine)
        publish()
        checkpoint()
    }

    /** Finished, ended early or left: the summary shows, or the screen goes, and the controls do nothing. */
    private val over: Boolean get() = engine.isDone || ended || leaving

    /**
     * Stops before the last round: records what was done and shows the summary. Without a finished
     * round there is nothing worth keeping, so the screen just leaves instead of calling this.
     */
    fun endEarly() {
        if (over) return
        stopTicker()
        running = false
        ended = true
        week = weekWithThis()
        repository.recordWorkout(config, activeSeconds, endedEarly = true, workSeconds = workSeconds)
        publish()
        checkpoint()
        release()
    }

    /** End from the notification: as End on the screen, which keeps a workout only once a round is done. */
    private fun endOffScreen() {
        if (over) return
        if (_state.value.hasProgress) {
            endEarly()
        } else {
            stopTicker()
            running = false
            leaving = true
            publish()
            release()
        }
    }

    /**
     * Saves [preset], named and maybe reshaped in the summary's editor, as a new one of the user's own,
     * and puts it on the Timer, as saving from the Timer or Presets does. The summary names it once the
     * save lands.
     */
    fun saveAsPreset(preset: Preset) {
        repository.saveAndApplyPreset(preset)
    }

    fun restart() {
        stopTicker()
        ended = false
        week = null
        activeMillis = 0
        workMillis = 0
        begin()
    }

    private fun begin() {
        val start = WorkoutEngine.start(config)
        engine = start.state
        running = true
        keep()
        play(start.cues)
        startTicker()
        publish()
        checkpoint()
    }

    /**
     * Picks up where [saved] stood. A workout that was running has run on since: the time away goes through
     * the engine like one long tick, so it is as far along as it would be, or over, with the one cue for where
     * it landed. One that was paused, or over, is shown as it was.
     */
    private fun restore(saved: WorkoutProgress) {
        running = saved.running
        ended = saved.ended
        week = saved.week
        activeMillis = saved.activeMillis
        workMillis = saved.workMillis
        if (running) applyStep(WorkoutEngine.tick(engine, clock.nowMillis() - saved.atEpochMillis))
        if (!over) {
            keep()
            if (running) startTicker()
            checkpoint()
        }
        publish()
    }

    /** Hands the engine every millisecond since the last tick, pauses excluded, however many: see the class. */
    private fun startTicker() {
        // Marked now, not once the coroutine gets to run: the time until then counts too.
        var last = timeSource.markNow()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                val now = timeSource.markNow()
                val elapsed = (now - last).inWholeMilliseconds
                last += elapsed.milliseconds
                applyStep(WorkoutEngine.tick(engine, elapsed))
            }
        }
    }

    private fun applyStep(step: Step) {
        val before = engine
        engine = step.state
        activeMillis += step.activeMillis
        workMillis += step.workMillis
        play(step.cues)
        if (engine.isDone) {
            running = false
            stopTicker()
            week = weekWithThis()
            repository.recordWorkout(config, activeSeconds, workSeconds = workSeconds)
            release()
        }
        publish()
        // A new interval is a change of course; the clock running down within one is not.
        if (engine.phase != before.phase || engine.round != before.round) checkpoint()
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun publish() {
        _state.value = uiState(running)
        val status = _state.value.status()
        if (kept && status != shown) {
            shown = status
            background.update(status)
        }
    }

    private fun keep() {
        if (kept) return
        kept = true
        background.start(controls)
    }

    private fun release() {
        if (!kept) return
        kept = false
        shown = null
        background.stop()
    }

    /** Saves where the workout stands now, for a process killed before the next change to come back to. */
    private fun checkpoint() {
        repository.saveWorkoutProgress(
            WorkoutProgress(
                id = id,
                state = engine,
                running = running,
                ended = ended,
                activeMillis = activeMillis,
                workMillis = workMillis,
                week = week,
                atEpochMillis = clock.nowMillis(),
            ),
        )
    }

    /** The screen has gone for good: nothing to keep going, in view or saved. */
    override fun onCleared() {
        stopTicker()
        release()
        repository.saveWorkoutProgress(null)
    }

    private fun uiState(running: Boolean): WorkoutUiState = engine.toUiState(
        running = running,
        ended = ended,
        presetName = presetName,
        elapsedSeconds = activeSeconds,
        presets = presets,
        keepScreenOn = keepScreenOn,
        calories = body.calories(workSeconds, activeSeconds - workSeconds, config.activity),
        week = week,
        leaving = leaving,
    )

    /**
     * The week as it stands with this workout in it, both kinds of workout counted as History counts
     * them. Worked out before the write, which lands later, so the summary has it as it arrives.
     */
    private fun weekWithThis(): WeekStanding {
        val today = timestamps.dayNumber(clock.nowMillis())
        val days = repository.history.value.map { timestamps.dayNumber(it.finishedAtEpochMillis) } +
            bells.sessions.value.mapNotNull { it.finishedAtEpochMillis }.map(timestamps::dayNumber) +
            today
        return weekStanding(days, today, repository.appSettings.value.weeklyGoal)
    }

    private fun play(cues: List<Cue>) {
        val settings = repository.cueSettings.value
        cues.forEach { playCue(it, settings, player) }
    }

    companion object {
        const val TICK_MILLIS = 100L
    }
}

/** What the notification shows of [this]: the phase, whether it is paused, the clock and the round. */
private fun WorkoutUiState.status() = WorkoutStatus(phase, paused, clock, round, rounds)

private fun Long.roundedSeconds(): Int = ((this + MILLIS_PER_SECOND / 2) / MILLIS_PER_SECOND).toInt()
