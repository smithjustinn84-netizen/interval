@file:OptIn(ExperimentalForeignApi::class)

package dev.justinsmith.bells.platform

import co.touchlab.kermit.Logger
import dev.justinsmith.bells.data.resources.Res
import dev.justinsmith.bells.data.resources.rest_over
import dev.justinsmith.bells.data.resources.rest_over_text
import dev.justinsmith.interval.platform.epochMillisNow
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

/**
 * One local notification for the end of the rest. iOS suspends the app soon after it leaves the screen, so
 * the session's own cue can't play there; the notification says the rest is over instead. While the app is
 * up the system shows none (the app has no notification delegate), and the session's beep is the cue.
 */
internal class IosRestAlerts : RestAlerts {
    private val log = Logger.withTag("IosRestAlerts")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val center: UNUserNotificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()

    /** Bumped by each start and each stop, so a scheduling overtaken by a stop adds nothing after it. */
    private var generation = 0

    override fun start(endAtEpochMillis: Long) {
        stop()
        val mine = generation
        scope.launch {
            // Reading the strings suspends: the rest may have been skipped meanwhile.
            val content = UNMutableNotificationContent().apply {
                setTitle(getString(Res.string.rest_over))
                setBody(getString(Res.string.rest_over_text))
                setSound(UNNotificationSound.defaultSound)
            }
            val seconds = (endAtEpochMillis - epochMillisNow()) / MILLIS_PER_SECOND
            if (mine != generation || seconds < MIN_TRIGGER_SECONDS) return@launch
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
            val request = UNNotificationRequest.requestWithIdentifier(IDENTIFIER, content, trigger)
            center.addNotificationRequest(request) { error ->
                if (error != null) log.w { "Couldn't schedule the rest's end: $error" }
            }
        }
    }

    override fun stop() {
        generation++
        center.removePendingNotificationRequestsWithIdentifiers(listOf(IDENTIFIER))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(IDENTIFIER))
    }

    private companion object {
        const val IDENTIFIER = "rest"
        const val MILLIS_PER_SECOND = 1_000.0
        const val MIN_TRIGGER_SECONDS = 1.0
    }
}
