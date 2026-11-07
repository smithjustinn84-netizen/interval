package dev.justinsmith.lift.platform

/*
 * Each platform's `liftPlatformModule` (Android's of a `Context`, iOS's and the desktop's of nothing) gives the
 * Koin definitions for what Lift needs from the platform: a builder for its database file
 * (`RoomDatabase.Builder<LiftDatabase>`), a [Beeper] over core's tone player, and the [SessionKeepAlive]. The
 * app's entry point builds it once per process.
 */

/**
 * The database's file name, inside the platform's app-data directory. It keeps the name from when Lift was called
 * Bells: renaming the file would leave every installed copy's data behind.
 */
internal const val LIFT_DATABASE = "bells.db"
