package dev.justinsmith.interval.platform

import androidx.room.RoomDatabase
import dev.justinsmith.interval.data.AppDatabase

/** Where the app keeps its files. Each platform puts them in its usual private app-data location. */
interface PlatformStorage {
    /** A builder for the database file; the driver and coroutine context are set in common code. */
    fun databaseBuilder(): RoomDatabase.Builder<AppDatabase>

    /** Absolute path of the settings file (DataStore Preferences). */
    val preferencesPath: String
}

/** The database's file name, inside the platform's app-data directory. */
const val DATABASE_NAME = "interval.db"

/** The settings file's name. DataStore requires the `.preferences_pb` extension. */
const val PREFERENCES_NAME = "interval.preferences_pb"
