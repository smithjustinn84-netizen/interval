package dev.justinsmith.interval.platform

import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger

/**
 * Starts [WorkoutService] for a workout and stops it when the workout is over. The service, which the system
 * builds, reaches the workout itself through the container ([ActiveWorkout]).
 */
internal class AndroidBackgroundWorkout(private val context: Context) : BackgroundWorkout {
    private val log = Logger.withTag("AndroidBackgroundWorkout")

    override fun start() {
        // Refused when the app is in the background (Android 12+). A workout starts from its screen, so
        // that is not expected; if it happens the workout runs on, without the notification.
        runCatching { context.startForegroundService(Intent(context, WorkoutService::class.java)) }
            .onFailure { log.w(it) { "Couldn't start the workout service" } }
    }

    override fun stop() {
        context.stopService(Intent(context, WorkoutService::class.java))
    }
}
