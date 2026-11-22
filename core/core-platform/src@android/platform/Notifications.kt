package dev.justinsmith.interval.platform

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

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

/**
 * Brings the app's task forward, as tapping its launcher icon does, rather than opening a second one. The
 * launcher's intent finds a task the launcher started; single top covers one started otherwise (a launcher
 * shortcut's, or `am start`), whose root intent differs, where it would put a second app over the first.
 */
fun Context.openAppIntent(requestCode: Int): PendingIntent? {
    val launch = packageManager.getLaunchIntentForPackage(packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) ?: return null
    // The launcher's entry is an alias, one an app icon ([AndroidAppIconSwitcher]), and the one enabled now may be
    // disabled by the time this is tapped: name the activity behind it, which a task is known by either way.
    launch.component?.let { entry -> activityBehind(entry) }?.let { launch.setClassName(packageName, it) }
    return PendingIntent.getActivity(this, requestCode, launch, PendingIntent.FLAG_IMMUTABLE)
}

/** The activity [entry] is an alias of; null when it is an activity itself, or can't be read. */
private fun Context.activityBehind(entry: ComponentName): String? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getActivityInfo(entry, PackageManager.ComponentInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getActivityInfo(entry, 0)
    }.targetActivity
}.getOrNull()

private const val SMALL_ICON_NAME = "ic_notification"
