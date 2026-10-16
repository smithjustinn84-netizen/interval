package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable
import androidx.room.RoomDatabase
import dev.justinsmith.interval.data.AppDatabase
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

/** Formats past moments for the workout history, in the device's time zone. */
interface TimestampFormatter {
    /** The moment's date, as a history section heads it: "Sunday, Oct 11". */
    fun date(epochMillis: Long): String

    /** The time of day: "7:42 PM". */
    fun time(epochMillis: Long): String

    /** The moment's calendar day, counted from any fixed day: equal for two moments on the same local day. */
    fun dayNumber(epochMillis: Long): Long

    /** The moment's day of the week, 1 for Monday through 7 for Sunday. */
    fun weekday(epochMillis: Long): Int
}

/** Where the app keeps its files. Each platform puts them in its usual private app-data location. */
interface PlatformStorage {
    /** A builder for the database file; the driver and coroutine context are set in common code. */
    fun databaseBuilder(): RoomDatabase.Builder<AppDatabase>

    /** Absolute path of the settings file (DataStore Preferences). */
    val preferencesPath: String
}

/** What the installed build says about itself, for the About screen. */
interface AppInfo {
    /**
     * The version the store shows, such as "0.1.0", read from the package or bundle; null when the
     * build carries none, as a desktop run from source does.
     */
    val version: String?
}

/** The database's file name, inside the platform's app-data directory. */
internal const val DATABASE_NAME = "interval.db"

/** The settings file's name. DataStore requires the `.preferences_pb` extension. */
internal const val PREFERENCES_NAME = "interval.preferences_pb"

/**
 * Koin definitions for platform services: [PlatformStorage], a [CuePlayer], a [TimestampFormatter]
 * and [AppInfo]. Built in composition because Android needs a `Context`.
 */
@Composable
expect fun rememberPlatformModule(): Module

/** Keeps the display awake while this is in the composition. */
@Composable
expect fun KeepScreenOn()

/**
 * Sets the system bars' icons to suit what is drawn under them: [lightIcons] for dark content (the
 * dark theme, the workout, the intro), whatever the system appearance. Android and iOS; desktop's
 * title bar follows the system.
 */
@Composable
expect fun SystemBarIcons(lightIcons: Boolean)

/**
 * Whether a back swipe previews the page underneath the Material way, shrinking and rounding the
 * page as it follows the finger (Android). Elsewhere navigation keeps its platform default, the
 * native slide on iOS.
 */
expect val materialBackPreview: Boolean

/**
 * Whether pages keep the platform's own push and pop transitions (iOS's native slide). Elsewhere
 * the app's run: Android's default is a slow fade and desktop's is none at all.
 */
expect val platformPageTransitions: Boolean

/**
 * Whether the user asked the system for less motion: Android's "Remove animations" (animator scale
 * 0) or iOS's Reduce Motion. Decorative animation stands still then.
 */
@Composable
expect fun prefersReducedMotion(): Boolean

/** Milliseconds since the Unix epoch. */
expect fun epochMillisNow(): Long

/** The wall clock, injected so that tests can fix the time. */
fun interface WallClock {
    fun nowMillis(): Long
}
