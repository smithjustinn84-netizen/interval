package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable
import androidx.room.RoomDatabase
import dev.justinsmith.interval.data.AppDatabase
import org.koin.core.module.Module

/** The sounds the app plays. Each platform maps them to tones it has. */
enum class Beep { Countdown, Halfway, Start, Work, Rest, Finished }

/** Vibration patterns, distinct enough to tell apart without looking. */
enum class Haptic { Work, Rest, Finished }

/** Plays workout feedback. Implementations swallow platform failures: a missing cue must never crash a workout. */
interface CuePlayer {
    fun beep(beep: Beep)

    fun speak(text: String)

    fun vibrate(haptic: Haptic)

    /** Frees platform resources (speech engines, tone generators). */
    fun release()
}

/** Formats past moments for the workout history, in the device's time zone. */
interface TimestampFormatter {
    /** The history section a moment falls in, relative to [nowMillis]: "Today", "Yesterday", "Sunday, Oct 11". */
    fun day(epochMillis: Long, nowMillis: Long): String

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

/** The database's file name, inside the platform's app-data directory. */
internal const val DATABASE_NAME = "interval.db"

/** The settings file's name. DataStore requires the `.preferences_pb` extension. */
internal const val PREFERENCES_NAME = "interval.preferences_pb"

/**
 * Koin definitions for platform services: [PlatformStorage], a [CuePlayer] and a
 * [TimestampFormatter]. Built in composition because Android needs a `Context`.
 */
@Composable
expect fun rememberPlatformModule(): Module

/** Keeps the display awake while this is in the composition. */
@Composable
expect fun KeepScreenOn()

/**
 * Sets the system bars' icons to suit what is drawn under them: [lightIcons] for dark or saturated
 * content (the dark theme, the workout's phase colours). Android only; iOS and desktop follow the
 * system appearance.
 */
@Composable
expect fun SystemBarIcons(lightIcons: Boolean)

/**
 * Whether a back swipe previews the page underneath the Material way, shrinking and rounding the
 * page as it follows the finger (Android). Elsewhere navigation keeps its platform default, the
 * native slide on iOS.
 */
expect val materialBackPreview: Boolean

/** Milliseconds since the Unix epoch. */
expect fun epochMillisNow(): Long

/** The wall clock, injected so that tests can fix the time. */
fun interface WallClock {
    fun nowMillis(): Long
}
