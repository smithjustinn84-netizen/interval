package dev.justinsmith.interval.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.platform.CuePlayer
import dev.justinsmith.interval.timer.Cue
import dev.justinsmith.interval.timer.MILLIS_PER_SECOND
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.Step
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.timer.WorkoutState
import dev.justinsmith.interval.timer.matching
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
 */
class WorkoutViewModel(
    private val repository: IntervalRepository,
    private val player: CuePlayer,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) : ViewModel() {
    private val config = repository.config.value

    /** Read once at start, like the configuration: a setting changed mid-workout applies next time. */
    private val keepScreenOn = repository.appSettings.value.keepScreenOn
    private var engine: WorkoutState
    private var running = false
    private var ended = false

    /** Time the timer has run, pauses excluded: skips shorten it, added time lengthens it. */
    private var activeMillis = 0L
    private val activeSeconds: Int get() = ((activeMillis + MILLIS_PER_SECOND / 2) / MILLIS_PER_SECOND).toInt()
    private var ticker: Job? = null

    // Followed rather than read on each publish: a preset saved from the summary lands later, and the
    // summary must then name it. Matched once per change, not on every tick.
    private var presets = repository.presets.value
    private var presetName = presets.matching(config)?.name
    private val _state: MutableStateFlow<WorkoutUiState>

    init {
        engine = WorkoutEngine.start(config).state
        _state = MutableStateFlow(uiState(running = true))
        // After _state: viewModelScope runs this at once, and its first value publishes.
        viewModelScope.launch {
            repository.presets.collect { all ->
                presets = all
                presetName = all.matching(config)?.name
                publish()
            }
        }
        begin()
    }

    val state: StateFlow<WorkoutUiState> = _state.asStateFlow()

    fun togglePause() {
        if (running) pause() else resume()
    }

    fun pause() {
        running = false
        stopTicker()
        publish()
    }

    fun resume() {
        if (over || ticker?.isActive == true) return
        running = true
        publish()
        ticker = viewModelScope.launch {
            var last = timeSource.markNow()
            while (isActive) {
                delay(TICK_MILLIS)
                val elapsed = (timeSource.markNow() - last).inWholeMilliseconds
                last += elapsed.milliseconds
                // Capped as the engine caps a tick, so a suspended app adds no more than the clock showed.
                activeMillis += elapsed.coerceAtMost(WorkoutEngine.MAX_TICK_MILLIS)
                applyStep(WorkoutEngine.tick(engine, elapsed))
            }
        }
    }

    /** Jumps to the next interval now. */
    fun skip() {
        if (over) return
        applyStep(WorkoutEngine.advance(engine))
    }

    fun back() {
        if (over) return
        engine = WorkoutEngine.back(engine)
        publish()
    }

    fun addTime() {
        if (over) return
        engine = WorkoutEngine.addTime(engine)
        publish()
    }

    /** Finished or ended early: the summary shows, and the controls do nothing. */
    private val over: Boolean get() = engine.isDone || ended

    /**
     * Stops before the last round: records what was done and shows the summary. Without a finished
     * round there is nothing worth keeping, so the screen just leaves instead of calling this.
     */
    fun endEarly() {
        if (over) return
        stopTicker()
        running = false
        ended = true
        repository.recordWorkout(config, elapsedSeconds = activeSeconds, endedEarly = true)
        publish()
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
        activeMillis = 0
        begin()
    }

    private fun begin() {
        val start = WorkoutEngine.start(config)
        engine = start.state
        play(start.cues)
        resume()
    }

    private fun applyStep(step: Step) {
        engine = step.state
        play(step.cues)
        if (engine.isDone) {
            running = false
            stopTicker()
            repository.recordWorkout(config, elapsedSeconds = activeSeconds)
        }
        publish()
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun publish() {
        _state.value = uiState(running)
    }

    private fun uiState(running: Boolean): WorkoutUiState = engine.toUiState(
        running = running,
        ended = ended,
        presetName = presetName,
        elapsedSeconds = activeSeconds,
        presets = presets,
        keepScreenOn = keepScreenOn,
    )

    private fun play(cues: List<Cue>) {
        val settings = repository.cueSettings.value
        cues.forEach { playCue(it, settings, player) }
    }

    companion object {
        const val TICK_MILLIS = 100L
    }
}
