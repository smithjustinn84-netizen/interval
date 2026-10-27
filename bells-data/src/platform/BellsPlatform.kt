package dev.justinsmith.bells.platform

import androidx.compose.runtime.Composable
import org.koin.core.module.Module

/**
 * Koin definitions for what Bells needs from the platform: a builder for its database file
 * (`RoomDatabase.Builder<BellsDatabase>`), a [Beeper] and [RestAlerts]. Built in composition because Android
 * needs a `Context`.
 */
@Composable
expect fun rememberBellsPlatformModule(): Module

/** The database's file name, inside the platform's app-data directory. */
internal const val BELLS_DATABASE = "bells.db"
