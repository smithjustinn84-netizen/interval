package dev.justinsmith.interval.platform

import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger

/**
 * Starts [WorkoutService] for a workout and feeds it what to show; stops it when the workout is over. The
 * service, which the system builds, reaches the workout through the companion's [WorkoutService.status]
 * and [WorkoutService.controls].
 */
internal class AndroidBackgroundWorkout(private val context: Context) : BackgroundWorkout {
    private val log = Logger.withTag("AndroidBackgroundWorkout")

    override fun start(controls: WorkoutControls) {
        WorkoutService.controls = controls
        // Refused when the app is in the background (Android 12+). A workout starts from its screen, so
        // that is not expected; if it happens the workout runs on, without the notification.
        runCatching { context.startForegroundService(Intent(context, WorkoutService::class.java)) }
            .onFailure { log.w(it) { "Couldn't start the workout service" } }
    }

    override fun update(status: WorkoutStatus) {
        WorkoutService.status.value = status
    }

    override fun stop() {
        WorkoutService.controls = null
        WorkoutService.status.value = null
        context.stopService(Intent(context, WorkoutService::class.java))
    }
}
