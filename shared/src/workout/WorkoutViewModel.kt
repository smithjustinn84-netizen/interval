package dev.justinsmith.interval.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.CueSettings
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.platform.Beep
import dev.justinsmith.interval.platform.CuePlayer
import dev.justinsmith.interval.platform.Haptic
import dev.justinsmith.interval.timer.Cue
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.Step
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.timer.WorkoutState
import dev.justinsmith.interval.timer.ceilSeconds
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.matching
import dev.justinsmith.interval.timer.presetEntries
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

data class WorkoutUiState(
    val phase: Phase,
    val running: Boolean,
    val rounds: Int,
    /** Rounds whose work interval is behind us. */
    val completedRounds: Int,
    /** The round being worked, or null between work intervals. */
    val activeRound: Int?,
    val clock: String,
    val progress: Float,
    val label: String,
    val next: String,
    val meta: String,
    val summary: String,
    /** True once the workout was ended before its last round; it then shows its summary like a finished one. */
    val ended: Boolean = false,
    /** The summary's heading line: the preset's name, or "Custom", and the workout's shape. */
    val shape: String = "",
    /** Time spent, for the summary. */
    val elapsed: String = "",
    /** Offer "Save as preset" on the summary: no preset has this shape yet. */
    val canSavePreset: Boolean = false,
) {
    val isDone: Boolean get() = phase == Phase.Done || ended
    val paused: Boolean get() = !running && !isDone

    /** Ending now keeps a summary and a history entry only once a round is finished. */
    val hasProgress: Boolean get() = completedRounds > 0
}

/**
 * The screen's view of an engine state. [ended] marks a workout stopped early; [presetName] is the
 * preset matching its shape, if any.
 */
fun WorkoutState.toUiState(running: Boolean, ended: Boolean = false, presetName: String? = null): WorkoutUiState {
    val finished = isDone || ended
    val paused = !running && !finished
    val rounds = config.rounds
    val completed = when (phase) {
        Phase.Prep -> 0
        Phase.Work -> round - 1
        Phase.Rest -> round
        Phase.Done -> rounds
    }
    return WorkoutUiState(
        phase = phase,
        running = running,
        rounds = rounds,
        completedRounds = completed,
        activeRound = round.takeIf { phase == Phase.Work },
        clock = formatClock(ceilSeconds(remainingMillis)),
        progress = if (isDone) 0f else progress,
        label = if (paused) {
            "Paused"
        } else {
            when (phase) {
                Phase.Prep -> "Get ready"
                Phase.Work -> "Work"
                Phase.Rest -> "Rest"
                Phase.Done -> "Done"
            }
        },
        next = when (phase) {
            Phase.Prep -> "Next: work ${formatClock(config.workSeconds)}"

            Phase.Work -> when {
                isLastRound -> "Last round — finish strong"
                config.restSeconds == 0 -> "Next: round ${round + 1} of $rounds"
                else -> "Next: rest ${formatClock(config.restSeconds)}"
            }

            Phase.Rest -> "Next: round ${round + 1} of $rounds"

            Phase.Done -> ""
        },
        meta = if (finished) {
            ""
        } else {
            "Round $round of $rounds · ${formatClock(
                ceilSeconds(sessionRemainingMillis),
            )} left"
        },
        summary = if (ended) {
            "$completed of $rounds rounds · ${formatClock(elapsedSeconds)}"
        } else {
            "$rounds round${if (rounds == 1) "" else "s"} · ${formatClock(config.totalSeconds)} total"
        },
        ended = ended,
        shape = "${presetName ?: "Custom"} · ${config.description}",
        elapsed = formatClock(if (ended) elapsedSeconds else config.totalSeconds),
        canSavePreset = presetName == null,
    )
}

/** Whole seconds of the session behind us, the get-ready countdown included. */
val WorkoutState.elapsedSeconds: Int
    get() = (config.totalSeconds - ceilSeconds(sessionRemainingMillis)).coerceAtLeast(0)

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
        ticker?.cancel()
        ticker = null
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
        ticker?.cancel()
        ticker = null
        running = false
        ended = true
        repository.recordWorkout(config, elapsedSeconds = engine.elapsedSeconds)
        publish()
    }

    fun saveAsPreset(name: String) {
        repository.saveCurrentAsPreset(name)
        publish()
    }

    fun restart() {
        ticker?.cancel()
        ticker = null
        ended = false
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
            ticker?.cancel()
            ticker = null
            repository.recordWorkout(config)
        }
        publish()
    }

    private fun publish() {
        _state.value = uiState(running)
    }

    private fun uiState(running: Boolean): WorkoutUiState {
        val name = presetEntries(repository.customPresets.value).matching(config)?.name
        return engine.toUiState(running, ended, name)
    }

    private fun play(cues: List<Cue>) {
        val settings = repository.cueSettings.value
        cues.forEach { playCue(it, settings, player) }
    }

    companion object {
        const val TICK_MILLIS = 100L
    }
}

/** Turns an engine [Cue] into the sounds, speech and vibration the user has switched on. */
fun playCue(cue: Cue, settings: CueSettings, player: CuePlayer) {
    with(SwitchedPlayer(settings, player)) {
        when (cue) {
            is Cue.Countdown -> {
                beep(Beep.Countdown)
                say(cue.secondsLeft.toString())
            }

            Cue.Halfway -> if (settings.halfway) {
                if (settings.sound) beep(Beep.Halfway) else say("Halfway")
            }

            is Cue.PhaseStarted -> when (cue.phase) {
                Phase.Prep -> {
                    beep(Beep.Start)
                    say("Get ready")
                }

                Phase.Work -> {
                    beep(Beep.Work)
                    buzz(Haptic.Work)
                    say(if (cue.lastRound) "Last round. Work" else "Work")
                }

                Phase.Rest -> {
                    beep(Beep.Rest)
                    buzz(Haptic.Rest)
                    say("Rest")
                }

                Phase.Done -> Unit
            }

            Cue.Finished -> {
                beep(Beep.Finished)
                buzz(Haptic.Finished)
                say("Done. Nice work")
            }
        }
    }
}

/** A [CuePlayer] behind the user's switches: each output does nothing while it is turned off. */
private class SwitchedPlayer(private val settings: CueSettings, private val player: CuePlayer) {
    fun beep(beep: Beep) {
        if (settings.sound) player.beep(beep)
    }

    fun say(text: String) {
        if (settings.voice) player.speak(text)
    }

    fun buzz(haptic: Haptic) {
        if (settings.vibrate) player.vibrate(haptic)
    }
}
