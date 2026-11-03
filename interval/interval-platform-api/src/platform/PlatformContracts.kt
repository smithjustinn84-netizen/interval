package dev.justinsmith.interval.platform

import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.UpcomingChange
import dev.justinsmith.interval.timer.WorkoutOutline
import kotlinx.coroutines.flow.StateFlow

/** The sounds the app plays. Each platform maps them to tones it has. */
enum class Beep { Countdown, Halfway, Start, Work, Rest, Finished }

/** Vibration patterns, distinct enough to tell apart without looking. */
enum class Haptic { Work, Rest, Finished }

/** A line spoken during a workout. Players put it into words from the app's strings ([text]). */
sealed interface Speech {
    /** The last seconds of an interval: "3", "2", "1". */
    data class Countdown(val secondsLeft: Int) : Speech

    data object GetReady : Speech

    data class Work(val lastRound: Boolean) : Speech

    data object Rest : Speech

    data object WarmUp : Speech

    data object CoolDown : Speech

    data object Halfway : Speech

    data object Finished : Speech
}

/** Plays workout feedback. Implementations swallow platform failures: a missing cue must never crash a workout. */
interface CuePlayer {
    fun beep(beep: Beep)

    fun speak(speech: Speech)

    fun vibrate(haptic: Haptic)

    /** Frees platform resources (speech engines, tone generators). */
    fun release()
}

/** A running workout as the platform shows it off screen: the words of Android's notification. */
data class WorkoutStatus(
    val phase: Phase,
    val paused: Boolean,
    /** Time left in the interval, "m:ss". */
    val clock: String,
    val round: Int,
    val rounds: Int,
    /** The whole workout end to end, for Android's progress bar ([StatusBar]). */
    val outline: WorkoutOutline = WorkoutOutline.Empty,
)

/**
 * The workout being kept, as the platform sees it from outside the app's screens: what it shows off screen, what its
 * own controls do (the notification's Pause or Resume, and its End), and what it can ask of the workout to stand in
 * for it while the app is suspended. The process's one `WorkoutSession` is it, so Android's service, which the
 * system builds, reaches it through the container.
 */
interface ActiveWorkout {
    /** What it shows off screen, changing no more often than the clock does; null while no workout is kept. */
    val status: StateFlow<WorkoutStatus?>

    fun togglePause()

    /**
     * Ends the workout now, without the screen's question: with a round done it is recorded and its summary
     * waits in the app; with none it is dropped, as the screen's End drops it.
     */
    fun end()

    /**
     * Every change of phase ahead of the workout as it stands this instant, nearest first, down to the end
     * ([WorkoutEngine.upcomingChanges][dev.justinsmith.interval.timer.WorkoutEngine.upcomingChanges]). Empty
     * while it is paused or over: nothing is coming.
     */
    fun upcomingChanges(): List<UpcomingChange>

    /** The app has gone for good (Android's task swiped away): nothing to keep going, in view or saved. */
    fun leave()
}

/**
 * Keeps a workout counting and in view while the app is off screen, showing [ActiveWorkout.status]. Android runs
 * a foreground service with an ongoing notification: the phase, the clock, the round, and Pause or Resume and
 * End, which act on the [ActiveWorkout]. iOS, which suspends the app, schedules a local notification for each
 * change of phase ahead ([ActiveWorkout.upcomingChanges]) as the app leaves the screen, and drops them when it
 * comes back. Desktop keeps nothing: see its actual for why.
 */
interface BackgroundWorkout {
    /**
     * A workout has begun, or come back after a restart: from now until [stop], keep it going and in view. Its
     * [ActiveWorkout.status] is set by then.
     */
    fun start()

    /** The workout is over, or its screen has gone: nothing to keep going or show. */
    fun stop()
}

/**
 * One day's reminder: at [minuteOfDay] (after local midnight) on [epochDay], for [plans], the day's workouts
 * still to do, in the order they were planned.
 */
data class PlanReminder(val epochDay: Long, val minuteOfDay: Int, val plans: List<PlannedWorkout>)

/** How many days ahead the reminders reach. iOS keeps this many of its 64 pending requests for them. */
const val PLAN_REMINDER_DAYS = 14

/**
 * The platform's notifications for the plan: Android's alarms, iOS's calendar-triggered local notifications.
 * Desktop keeps none: a desktop window is not a phone in a pocket.
 */
interface PlanReminders {
    /**
     * Replaces whatever is pending with [reminders], each a notification at its day and time; empty cancels
     * them all.
     */
    suspend fun schedule(reminders: List<PlanReminder>)
}

/**
 * The plan's reminders as they now stand, for what runs as one is due with no screen open: Android's alarm, which
 * words its notification from [reminderOn] as it shows and then has the reminders worked out again
 * ([reschedule]), so they reach two weeks ahead of the last one shown, whether or not the app is opened. History's
 * `PlanReminderScheduler` is it.
 */
interface PlanReminderSource {
    /** The reminder on [epochDay] as the plan now stands, its time passed or not; null when none is due then. */
    suspend fun reminderOn(epochDay: Long): PlanReminder?

    /**
     * Works out the reminders from now and hands them to [PlanReminders], returning once it has them: even the
     * same set as before, which a change of clock or time zone needs set again.
     */
    suspend fun reschedule()
}

/**
 * The colours a workout is shown in off screen, as ARGB: Android's notification and its progress bar, tinted by
 * the phase. The design system's phase colours, which the app passes in so the platform draws without it.
 */
data class StatusColors(val prep: Int, val work: Int, val rest: Int, val done: Int, val paused: Int) {
    /** [phase]'s colour; the warm-up shares get ready's, the cool-down rest's. */
    fun of(phase: Phase): Int = when (phase) {
        Phase.Prep, Phase.WarmUp -> prep
        Phase.Work -> work
        Phase.Rest, Phase.CoolDown -> rest
        Phase.Done -> done
    }
}

/*
 * Each platform's `platformModule` (in `interval-platform`; Android's of a `Context`, iOS's and the desktop's of
 * nothing) gives the Koin definitions for the services above: a [CuePlayer], a [BackgroundWorkout] and
 * [PlanReminders], with the data module's `PlatformStorage`. The [ActiveWorkout] a [BackgroundWorkout] shows is
 * the workout feature's, as are the [StatusColors]. What both sides take (the clock, the formatter, `AppInfo`
 * and the sharer) is core's `corePlatformModule`. The app's entry point builds it once per process.
 */
