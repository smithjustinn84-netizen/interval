@file:OptIn(ExperimentalForeignApi::class)

package dev.justinsmith.interval.platform

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.timer.MINUTES_PER_HOUR
import dev.justinsmith.interval.timer.dateOfEpochDay
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * One local notification per reminder, on a calendar trigger for its day and time: date components with no
 * time zone of their own fire by the device's zone as it stands then, so a zone change moves them with the
 * user. Its identifier is its day, so the set is replaced by removing every day a reminder could still be
 * pending for, around today, and adding the new ones; no record is kept. Each is worded here, as it is
 * scheduled, since reading the app's strings suspends. A reminder due while the app is open is not shown:
 * the app installs no notification-centre delegate, and the plan is on screen for the asking.
 */
internal class IosPlanReminders : PlanReminders {
    private val log = Logger.withTag("IosPlanReminders")
    private val center: UNUserNotificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun schedule(reminders: List<PlanReminder>) {
        val worded = reminders.map { it to reminderWords(it) }
        center.removePendingNotificationRequestsWithIdentifiers(possibleIdentifiers())
        center.removeDeliveredNotificationsWithIdentifiers(possibleIdentifiers())
        for ((reminder, words) in worded) {
            val content = UNMutableNotificationContent().apply {
                setTitle(words.title)
                setBody(words.body)
                setSound(UNNotificationSound.defaultSound)
            }
            val date = dateOfEpochDay(reminder.epochDay)
            val parts = NSDateComponents().apply {
                year = date.year.toLong()
                month = date.month.toLong()
                day = date.day.toLong()
                hour = (reminder.minuteOfDay / MINUTES_PER_HOUR).toLong()
                minute = (reminder.minuteOfDay % MINUTES_PER_HOUR).toLong()
            }
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(parts, repeats = false)
            val request = UNNotificationRequest.requestWithIdentifier(identifier(reminder.epochDay), content, trigger)
            center.addNotificationRequest(request) { error ->
                if (error != null) log.w { "Couldn't schedule the reminder for day ${reminder.epochDay}: $error" }
            }
        }
    }

    /**
     * The identifiers of every reminder that could still be pending: the last set reached [PLAN_REMINDER_DAYS]
     * past the day it was made on, and one made before today has fired for the days before it. Today is
     * counted in UTC, a day out at most, which the margin covers.
     */
    private fun possibleIdentifiers(): List<String> {
        val today = epochMillisNow().floorDiv(DAY_MILLIS)
        return ((today - PLAN_REMINDER_DAYS - 1)..(today + PLAN_REMINDER_DAYS + 1)).map(::identifier)
    }

    private fun identifier(epochDay: Long) = "$REQUEST_PREFIX$epochDay"

    private companion object {
        const val REQUEST_PREFIX = "plan-reminder-"
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
