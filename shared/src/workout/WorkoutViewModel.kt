package dev.justinsmith.interval.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.platform.CuePlayer
import dev.justinsmith.interval.timer.Cue
import dev.justinsmith.interval.timer.MILLIS_PER_SECOND
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
    private var engine: WorkoutState
    private var running = false
    private var ended = false

    /** Time the timer has run, pauses excluded: skips shorten it, added time lengthens it. */
    private var activeMillis = 0L
    private val activeSeconds: Int get() = ((activeMillis + MILLIS_PER_SECOND / 2) / MILLIS_PER_SECOND).toInt()
    private var ticker: Job? = null
    private val _state: MutableStateFlow<WorkoutUiState>

    init {
        val start = WorkoutEngine.start(config)
        engine = start.state
        _state = MutableStateFlow(uiState(running = true))
        play(start.cues)
        resume()
    }

    val state: StateFlow<WorkoutUiState> = _state.asStateFlow()

    /** Read once at start, like the configuration: a setting changed mid-workout applies next time. */
    val keepScreenOn: Boolean = repository.appSettings.value.keepScreenOn

    fun togglePause() {
        if (running) pause() else resume()
    }

    fun pause() {
        running = false
        stopTicker()
        publish()
    }

    fun resume() {
        if (engine.isDone || ended || ticker?.isActive == true) return
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
        if (!engine.isDone) applyStep(WorkoutEngine.advance(engine))
    }

    fun back() {
        engine = WorkoutEngine.back(engine)
        publish()
    }

    fun addTime() {
        engine = WorkoutEngine.addTime(engine)
        publish()
    }

    /**
     * Stops before the last round: records what was done and shows the summary. Without a finished
     * round there is nothing worth keeping, so the screen just leaves instead of calling this.
     */
    fun endEarly() {
        if (engine.isDone || ended) return
        stopTicker()
        running = false
        ended = true
        repository.recordWorkout(config, elapsedSeconds = activeSeconds, endedEarly = true)
        publish()
    }

    fun saveAsPreset(name: String) {
        repository.saveCurrentAsPreset(name)
        publish()
    }

    fun restart() {
        stopTicker()
        ended = false
        activeMillis = 0
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

    private fun uiState(running: Boolean): WorkoutUiState {
        val presets = repository.presets.value
        return engine.toUiState(
            running = running,
            ended = ended,
            presetName = presets.matching(config)?.name,
            elapsedSeconds = activeSeconds,
            nextCustomNumber = presets.count { it.custom } + 1,
        )
    }

    private fun play(cues: List<Cue>) {
        val settings = repository.cueSettings.value
        cues.forEach { playCue(it, settings, player) }
    }

    companion object {
        const val TICK_MILLIS = 100L
    }
}
