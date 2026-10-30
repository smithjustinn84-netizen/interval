package dev.justinsmith.lift.platform

import androidx.compose.runtime.Composable
import org.koin.core.module.Module

/**
 * Koin definitions for what Lift needs from the platform: a builder for its database file
 * (`RoomDatabase.Builder<LiftDatabase>`), a [Beeper] and [RestAlerts]. Built in composition because Android
 * needs a `Context`.
 */
@Composable
expect fun rememberLiftPlatformModule(): Module

/**
 * The database's file name, inside the platform's app-data directory. It keeps the name from when Lift was called
 * Bells: renaming the file would leave every installed copy's data behind.
 */
internal const val LIFT_DATABASE = "bells.db"
