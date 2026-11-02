package dev.justinsmith.interval.workout

import dev.justinsmith.interval.data.SettingsStore
import dev.justinsmith.interval.data.WorkoutProgressStore
import dev.justinsmith.interval.model.BodySettings
import dev.justinsmith.interval.model.CueSettings
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.platform.ActiveWorkout
import dev.justinsmith.interval.platform.BackgroundWorkout
import dev.justinsmith.interval.platform.CuePlayer
import dev.justinsmith.interval.platform.HealthStore
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.WorkoutStatus
import dev.justinsmith.interval.platform.playCue
import dev.justinsmith.interval.timer.Cue
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.Step
import dev.justinsmith.interval.timer.UpcomingChange
import dev.justinsmith.interval.timer.WorkoutEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The running workout, owned by the process rather than by its screen: one at a time, from the moment the Workout
 * page opens it ([open]) until the page has gone for good ([leave]), through the screen being destroyed and built
 * again. The [ticker] feeds the engine the time gone, cues go to the [CuePlayer], and a finished workout goes to
 * the [recorder], which keeps it in History and hands it to the [HealthStore] with its course.
 *
 * Off screen it is kept going and in view by [background], which shows [status] and acts on it through
 * [ActiveWorkout]. Its course is saved in the [progressStore] at each change ([WorkoutRun.toProgress]), so a process
 * killed mid-workout picks it up where it had got to when the page opens it again, or shows the summary of what
 * ran, instead of starting again. Everything here runs on the main thread, the screen's actions and the
 * notification's alike.
 */
// One per control, and the summary's three: how it felt, its words for the health store, and the heart rate.
@Suppress("detekt.TooManyFunctions")
class WorkoutSession(
    private val settingsStore: SettingsStore,
    private val progressStore: WorkoutProgressStore,
    private val player: CuePlayer,
    private val clock: WallClock,
    private val background: BackgroundWorkout,
    private val recorder: WorkoutRecorder,
    private val ticker: WorkoutTicker,
) : ActiveWorkout {
    private val _state = MutableStateFlow<WorkoutRun?>(null)
    private val _status = MutableStateFlow<WorkoutStatus?>(null)

    /** True between [BackgroundWorkout.start] and [BackgroundWorkout.stop]: while there is a workout to keep. */
    private var kept = false

    /** The workout held, null when none is; set, it is published, and shown off screen while it is kept. */
    private var run: WorkoutRun?
        get() = _state.value
        set(value) {
            _state.value = value
            if (kept && value != null) _status.value = value.status()
        }

    private val tick: (Long) -> Unit = { elapsed -> run?.let { applyStep(WorkoutEngine.tick(it.engine, elapsed)) } }

    /** The workout held, as it stands; null when none is. */
    val state: StateFlow<WorkoutRun?> = _state.asStateFlow()

    override val status: StateFlow<WorkoutStatus?> = _status.asStateFlow()

    /**
     * The Workout page [id] is open: the workout it shows, if this is it already; else the one saved under [id]
     * picked up where it stood (a process killed under it); else a new one from the Timer's configuration. A new
     * one ends whatever workout was held before.
     */
    fun open(id: Long) {
        if (run?.id == id) return
        val saved = progressStore.workoutProgress.value?.takeIf { it.id == id }
        leave()
        val settings = settingsStore.appSettings.value
        if (saved == null) {
            begin(id, settingsStore.config.value, settings.keepScreenOn, settings.body)
            return
        }
        run = WorkoutRun.restored(saved, settings.keepScreenOn, settings.body)
        // One that was running has run on since: the time away goes through the engine like one long tick, so it
        // is as far along as it would be, or over, with the one cue for where it landed. One that was paused, or
        // over, is shown as it was.
        if (saved.running) applyStep(WorkoutEngine.tick(saved.state, clock.nowMillis() - saved.atEpochMillis))
        val restored = run ?: return
        if (!restored.over) {
            hold(keep = true)
            if (restored.running) ticker.start(tick)
            checkpoint()
        }
    }

    override fun togglePause() {
        val current = run?.takeUnless { it.over } ?: return
        val now = clock.nowMillis()
        if (current.running) {
            ticker.stop()
            run = current.copy(running = false).turned(phase = null, now)
        } else {
            ticker.start(tick)
            run = current.copy(running = true).turned(current.engine.phase, now)
        }
        checkpoint()
    }

    /** Jumps to the next interval now. */
    fun skip() {
        val current = run?.takeUnless { it.over } ?: return
        applyStep(WorkoutEngine.advance(current.engine))
        checkpoint()
    }

    fun back() {
        val current = run?.takeUnless { it.over } ?: return
        val moved = current.copy(engine = WorkoutEngine.back(current.engine))
        run = if (moved.running) moved.turned(moved.engine.phase, clock.nowMillis()) else moved
        checkpoint()
    }

    fun addTime() {
        val current = run?.takeUnless { it.over } ?: return
        run = current.copy(engine = WorkoutEngine.addTime(current.engine))
        checkpoint()
    }

    /**
     * Ends the workout now, from the screen or the notification. With a round done it is recorded, ended early,
     * and its summary shows; with none there is nothing worth keeping, and the screen leaves. In the cool-down
     * every round is done: ending there finishes the workout, as its end would.
     */
    override fun end() {
        val current = run?.takeUnless { it.over } ?: return
        when {
            current.engine.phase == Phase.CoolDown -> skip()

            current.engine.completedRounds > 0 -> {
                ticker.stop()
                val stopped = current.copy(running = false, ended = true).turned(phase = null, clock.nowMillis())
                run = recorder.finished(stopped, endedEarly = true)
                checkpoint()
                hold(keep = false)
            }

            else -> {
                ticker.stop()
                run = current.copy(running = false, leaving = true)
                hold(keep = false)
            }
        }
    }

    /** The same workout again from Get ready, from its summary; what the summary said goes to the health store. */
    fun restart() {
        val current = run ?: return
        recorder.rewriteForHealth(current)
        ticker.stop()
        begin(current.id, current.config, current.keepScreenOn, current.body)
    }

    /** How hard the finished workout felt and the summary's note as typed: kept in History with it at once. */
    fun reflect(effort: PerceivedEffort?, note: String) {
        val current = run ?: return
        val reflected = current.copy(effort = effort, note = note.take(PerceivedEffort.NOTE_MAX_LENGTH))
        run = reflected
        if (reflected.entryUid == null) return
        recorder.reflect(reflected)
        checkpoint()
    }

    /**
     * The summary's effort and note in words, for the health store's note: the screen words them, as it words
     * everything. Written to the store as the summary goes ([leave], [restart]), not at every keystroke.
     */
    fun describeForHealth(notes: String?) {
        run = run?.copy(healthNotes = notes)
    }

    /**
     * Reads the finished workout's heart rate from the health store, for its summary, unless it has it; kept in
     * History once found. The screen asks as the summary shows: a watch may not have synced yet, and History's page
     * asks again.
     */
    suspend fun readHeartRate() {
        val finished = run?.takeIf { it.entryUid != null && it.heartRate == null } ?: return
        val heartRate = recorder.heartRate(finished) ?: return
        run = run?.takeIf { it.entryUid == finished.entryUid }?.copy(heartRate = heartRate) ?: return
    }

    // Up to a tick behind the clock: the platform's notifications land within a tenth of a second of the cue.
    override fun upcomingChanges(): List<UpcomingChange> {
        val current = run ?: return emptyList()
        return if (current.running && !current.over) WorkoutEngine.upcomingChanges(current.engine) else emptyList()
    }

    /** The Workout page has gone for good: nothing to keep going, in view or saved. */
    override fun leave() {
        val current = run ?: return
        recorder.rewriteForHealth(current)
        ticker.stop()
        hold(keep = false)
        run = null
        progressStore.saveWorkoutProgress(null)
    }

    private fun begin(id: Long, config: IntervalConfig, keepScreenOn: Boolean, body: BodySettings) {
        val start = WorkoutEngine.start(config)
        run =
            WorkoutRun(
                id,
                start.state,
                running = true,
                startedAt = clock.nowMillis(),
                keepScreenOn = keepScreenOn,
                body = body,
            )
        hold(keep = true)
        player.play(start.cues, settingsStore.cueSettings.value)
        ticker.start(tick)
        checkpoint()
    }

    private fun applyStep(step: Step) {
        val before = run ?: return
        val next = before.stepped(step, clock.nowMillis())
        player.play(step.cues, settingsStore.cueSettings.value)
        if (next.engine.isDone) {
            ticker.stop()
            val finished = recorder.finished(next.turned(phase = null, clock.nowMillis()), endedEarly = false)
            hold(keep = false)
            run = finished
        } else {
            run = next
        }
        // A new interval is a change of course; the clock running down within one is not.
        if (next.turnedFrom(before.engine)) checkpoint()
    }

    /** Keeps the workout going and in view off screen, or lets it go; the platform hears of each once. */
    private fun hold(keep: Boolean) {
        if (keep == kept) return
        kept = keep
        _status.value = if (keep) run?.status() else null
        if (keep) background.start() else background.stop()
    }

    /** Saves where the workout stands now, for a process killed before the next change to come back to. */
    private fun checkpoint() {
        val current = run ?: return
        progressStore.saveWorkoutProgress(current.toProgress(clock.nowMillis()))
    }
}

private fun CuePlayer.play(cues: List<Cue>, settings: CueSettings) = cues.forEach { playCue(it, settings, this) }
