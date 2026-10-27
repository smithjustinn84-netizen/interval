package dev.justinsmith.interval.platform

import android.app.PendingIntent
import android.content.Context

// What the workout's notification, the plan's reminders and Lift's rest share.

/**
 * The app's own mark (`ic_notification`, generated with the launcher icons into the app's resources, so
 * found by name from here), or the system's alarm icon in a build without it.
 */
fun Context.notificationIcon(): Int {
    @Suppress("DiscouragedApi") // Once per notification, for one resource another module owns.
    val own = resources.getIdentifier(SMALL_ICON_NAME, "drawable", packageName)
    return if (own != 0) own else android.R.drawable.ic_lock_idle_alarm
}

/** Brings the app's task forward, as tapping its launcher icon does, rather than opening a second one. */
fun Context.openAppIntent(requestCode: Int): PendingIntent? {
    val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
    return PendingIntent.getActivity(this, requestCode, launch, PendingIntent.FLAG_IMMUTABLE)
}

private const val SMALL_ICON_NAME = "ic_notification"
