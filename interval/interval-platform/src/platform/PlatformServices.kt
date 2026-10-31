package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.UpcomingChange
import org.koin.core.module.Module

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
)

/**
 * The platform's hold on a running workout: what its own controls do (the notification's Pause or Resume,
 * and its End), and what it can ask of the workout to stand in for it while the app is suspended.
 */
interface WorkoutControls {
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
}

/**
 * Keeps a workout counting and in view while the app is off screen. Android runs a foreground service
 * with an ongoing notification: the phase, the clock, the round, and Pause or Resume and End, which act on
 * the workout through its [WorkoutControls]. iOS, which suspends the app, schedules a local notification for
 * each change of phase ahead ([WorkoutControls.upcomingChanges]) as the app leaves the screen, and drops them
 * when it comes back. Desktop keeps nothing: see its actual for why.
 */
interface BackgroundWorkout {
    /** A workout has begun, or come back after a restart: from now until [stop], keep it going and in view. */
    fun start(controls: WorkoutControls)

    /** The workout has moved: what it shows now. Called no more often than the clock changes. */
    fun update(status: WorkoutStatus)

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
 * Asks for whatever the platform needs before it can show a notification: Android 13's permission to post
 * them, once, the first time (the system stops asking after two refusals); iOS's authorization, which the
 * system asks for once and then answers from the user's settings. Desktop needs nothing. Asked as a workout
 * starts, for its notification off screen, and as the plan's reminder is turned on.
 */
@Composable
expect fun AskToShowNotifications()

/**
 * Koin definitions for the interval side's platform services: [PlatformStorage], a [CuePlayer], a
 * [BackgroundWorkout] and [PlanReminders]. What both sides take (the clock, the formatter, [AppInfo] and the
 * sharer) is core's `rememberCorePlatformModule`. Built in composition because Android needs a `Context`.
 */
@Composable
expect fun rememberPlatformModule(): Module
