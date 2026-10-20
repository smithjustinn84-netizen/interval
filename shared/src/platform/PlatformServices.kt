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
 * Koin definitions for platform services: [PlatformStorage], a [CuePlayer], a [TimestampFormatter]
 * and [AppInfo]. Built in composition because Android needs a `Context`.
 */
@Composable
expect fun rememberPlatformModule(): Module
