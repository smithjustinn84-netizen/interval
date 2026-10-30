package dev.justinsmith.bells.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import co.touchlab.kermit.Logger
import dev.justinsmith.bells.data.resources.Res
import dev.justinsmith.bells.data.resources.rest_channel
import dev.justinsmith.bells.data.resources.rest_channel_description
import dev.justinsmith.bells.data.resources.rest_left
import dev.justinsmith.bells.data.resources.rest_title
import dev.justinsmith.interval.platform.notificationIcon
import dev.justinsmith.interval.platform.openAppIntent
import dev.justinsmith.interval.timer.formatClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * Starts and stops [RestService] for a rest; the service counts it down itself from the end it is given. Both go
 * to the service as commands, which it takes in order: it has gone to the foreground with a start before it sees
 * the stop that follows, as the system demands of a service started for the foreground.
 */
internal class AndroidRestAlerts(private val context: Context) : RestAlerts {
    private val log = Logger.withTag("AndroidRestAlerts")
    private var running = false

    override fun start(endAtEpochMillis: Long) {
        // A rest shortened past the time it has run is already over: nothing to count down.
        if (endAtEpochMillis <= System.currentTimeMillis()) {
            stop()
            return
        }
        val intent = Intent(context, RestService::class.java).putExtra(RestService.EXTRA_END_AT, endAtEpochMillis)
        // Refused from the background (Android 12+). A set is logged from the session's screen, so that is
        // not expected; if it happens the rest runs on, without the notification.
        runCatching { context.startForegroundService(intent) }
            .onSuccess { running = true }
            .onFailure { log.w(it) { "Couldn't start the rest service" } }
    }

    override fun stop() {
        if (!running) return
        running = false
        // Refused only once the service has gone on its own, with the app in the background: nothing to stop.
        val intent = Intent(context, RestService::class.java).setAction(RestService.ACTION_STOP)
        runCatching { context.startService(intent) }
            .onFailure { log.d(it) { "The rest service had already gone" } }
    }
}

/**
 * The foreground service behind a rest between sets. It holds the CPU awake so the session's 3-2-1 and Start
 * play with the screen off, and shows the rest counting down as an ongoing notification. It ends with the
 * rest, or when the session skips it. Special use, as the workout's is: a rest can run ten minutes, past a
 * short service's three.
 */
class RestService : Service() {
    private val log = Logger.withTag("RestService")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var countdown: Job? = null
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // The channel must exist before the first notification, which startForeground posts at once. As for the
        // workout's: the app's name holds the place on the very first run, and the words follow.
        if (notifications?.getNotificationChannel(CHANNEL_ID) == null) createChannel(appName(), null)
        scope.launch {
            createChannel(getString(Res.string.rest_channel), getString(Res.string.rest_channel_description))
        }
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
    }

    // Each stop is for the commands before it: a rest started after it (its start, a later id) keeps the service.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val endAt = intent?.getLongExtra(EXTRA_END_AT, 0L) ?: 0L
        // Started for the foreground, so to the foreground first, whatever follows: stopping short of it
        // crashes the app.
        goForeground(endAt)
        if (endAt <= System.currentTimeMillis()) {
            stopSelf(startId)
        } else {
            countDown(endAt)
        }
        return START_NOT_STICKY
    }

    // At once, on the main thread, so without the words, which suspend: the countdown's first tick adds them.
    private fun goForeground(endAt: Long) {
        val first = builder().setContentTitle(appName()).setContentText(formatClock(secondsLeft(endAt))).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, first)
        }
    }

    private fun countDown(endAt: Long) {
        runCatching { wakeLock?.takeUnless { it.isHeld }?.acquire(WAKE_LOCK_TIMEOUT_MILLIS) }
            .onFailure { log.w(it) { "Couldn't hold the wake lock" } }
        countdown?.cancel()
        countdown = scope.launch {
            while (System.currentTimeMillis() < endAt) {
                notifications?.notify(NOTIFICATION_ID, notification(endAt))
                delay(TICK_MILLIS)
            }
            // The session's clock plays the cue as the rest ends; a moment later this is over too.
            delay(LINGER_MILLIS)
            stopSelf()
        }
    }

    /** The user swiped the app away: its session's rest went with it. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
            .onFailure { log.w(it) { "Couldn't release the wake lock" } }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createChannel(name: CharSequence, description: String?) {
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
            this.description = description
            setShowBadge(false)
        }
        notifications?.createNotificationChannel(channel)
    }

    /** "Resting" over the time left, silent and ongoing, on the lock screen; tapping it opens the app. */
    private suspend fun notification(endAt: Long): Notification = builder()
        .setContentTitle(getString(Res.string.rest_title))
        .setContentText(getString(Res.string.rest_left, formatClock(secondsLeft(endAt))))
        .build()

    private fun secondsLeft(endAt: Long): Int {
        val leftMillis = (endAt - System.currentTimeMillis()).coerceAtLeast(0)
        return ((leftMillis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
    }

    private fun appName(): CharSequence = applicationInfo.loadLabel(packageManager)

    /** What every version of the notification shares. */
    private fun builder(): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(notificationIcon())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(REQUEST_OPEN))

    companion object {
        internal const val EXTRA_END_AT = "dev.justinsmith.bells.REST_END_AT"
        internal const val ACTION_STOP = "dev.justinsmith.bells.REST_STOP"
        private const val CHANNEL_ID = "rest"

        /** Beside the workout's, which is 1. */
        private const val NOTIFICATION_ID = 2
        private const val REQUEST_OPEN = 10
        private const val WAKE_LOCK_TAG = "interval:rest"
        private const val TICK_MILLIS = 1_000L
        private const val MILLIS_PER_SECOND = 1_000L
        private const val LINGER_MILLIS = 1_500L

        /** Let go on its own after this long, well past the longest rest, should the stop somehow not come. */
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 15L * 60 * 1000
    }
}
