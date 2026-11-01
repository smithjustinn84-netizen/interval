@file:OptIn(ExperimentalForeignApi::class)

package dev.justinsmith.lift.platform

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.platform.epochMillisNow
import dev.justinsmith.lift.platform.resources.Res
import dev.justinsmith.lift.platform.resources.emom_over
import dev.justinsmith.lift.platform.resources.hold_over
import dev.justinsmith.lift.platform.resources.rest_over
import dev.justinsmith.lift.platform.resources.rest_over_text
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

/**
 * One local notification for the end of the run: a rest, an EMOM or a hold. iOS suspends the app soon after it
 * leaves the screen, so the session's own cue can't play there; the notification says the run is over instead.
 * While the app is up the system shows none (the app's notification delegate shows only plan reminders), and the
 * session's beep is the cue.
 */
internal class IosSessionKeepAlive : SessionKeepAlive {
    private val log = Logger.withTag("IosSessionKeepAlive")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val center: UNUserNotificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()

    /** Bumped by each start and each stop, so a scheduling overtaken by a stop adds nothing after it. */
    private var generation = 0

    override fun start(run: TimerRun) {
        stop()
        val mine = generation
        scope.launch {
            // Reading the strings suspends: the run may have been stopped meanwhile.
            val title = when (run) {
                is TimerRun.Rest -> Res.string.rest_over
                is TimerRun.Emom -> Res.string.emom_over
                is TimerRun.Hold -> Res.string.hold_over
            }
            val content = UNMutableNotificationContent().apply {
                setTitle(getString(title))
                setBody(getString(Res.string.rest_over_text))
                setSound(UNNotificationSound.defaultSound)
            }
            val seconds = (run.endAtEpochMillis - epochMillisNow()) / MILLIS_PER_SECOND
            if (mine != generation || seconds < MIN_TRIGGER_SECONDS) return@launch
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
            val request = UNNotificationRequest.requestWithIdentifier(IDENTIFIER, content, trigger)
            center.addNotificationRequest(request) { error ->
                if (error != null) log.w { "Couldn't schedule the run's end: $error" }
            }
        }
    }

    /** The container is closing: drop any scheduling still under way. */
    fun release() {
        scope.cancel()
    }

    override fun stop() {
        generation++
        center.removePendingNotificationRequestsWithIdentifiers(listOf(IDENTIFIER))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(IDENTIFIER))
    }

    private companion object {
        /** The rests' identifier before EMOMs and holds had one: one run is kept at a time. */
        const val IDENTIFIER = "rest"
        const val MILLIS_PER_SECOND = 1_000.0
        const val MIN_TRIGGER_SECONDS = 1.0
    }
}
