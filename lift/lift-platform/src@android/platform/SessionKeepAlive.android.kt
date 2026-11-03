package dev.justinsmith.lift.platform

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
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.notificationIcon
import dev.justinsmith.interval.platform.openAppIntent
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.lift.domain.EmomClock
import dev.justinsmith.lift.platform.resources.Res
import dev.justinsmith.lift.platform.resources.emom_title
import dev.justinsmith.lift.platform.resources.hold_title
import dev.justinsmith.lift.platform.resources.rest_title
import dev.justinsmith.lift.platform.resources.timer_channel
import dev.justinsmith.lift.platform.resources.timer_channel_description
import dev.justinsmith.lift.platform.resources.timer_left
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Starts and stops [TimerService] for a run; the service counts it down itself from the end it is given. Both go
 * to the service as commands, which it takes in order: it has gone to the foreground with a start before it sees
 * the stop that follows, as the system demands of a service started for the foreground.
 */
internal class AndroidSessionKeepAlive(private val context: Context, private val clock: WallClock) : SessionKeepAlive {
    private val log = Logger.withTag("AndroidSessionKeepAlive")
    private var running = false

    override fun start(run: TimerRun) {
        // A rest shortened past the time it has run is already over: nothing to count down.
        if (run.endAtEpochMillis <= clock.nowMillis()) {
            stop()
            return
        }
        val intent = Intent(context, TimerService::class.java)
            .putExtra(TimerService.EXTRA_KIND, run.kind)
            .putExtra(TimerService.EXTRA_STARTED_AT, run.startedAtEpochMillis)
            .putExtra(TimerService.EXTRA_END_AT, run.endAtEpochMillis)
            .putExtra(TimerService.EXTRA_MINUTES, (run as? TimerRun.Emom)?.minutes ?: 0)
        // Refused from the background (Android 12+), unless this service is already in the foreground: a run
        // starts from the session's screen, or off screen only from the one before it (a hold's end into its
        // rest), the service still up. If it is refused anyway, the run goes on, without the notification.
        runCatching { context.startForegroundService(intent) }
            .onSuccess { running = true }
            .onFailure { log.w(it) { "Couldn't start the timer service" } }
    }

    override fun stop() {
        if (!running) return
        running = false
        // Refused only once the service has gone on its own, with the app in the background: nothing to stop.
        val intent = Intent(context, TimerService::class.java).setAction(TimerService.ACTION_STOP)
        runCatching { context.startService(intent) }
            .onFailure { log.d(it) { "The timer service had already gone" } }
    }

    private val TimerRun.kind: String
        get() = when (this) {
            is TimerRun.Rest -> TimerService.KIND_REST
            is TimerRun.Emom -> TimerService.KIND_EMOM
            is TimerRun.Hold -> TimerService.KIND_HOLD
        }
}

/**
 * The foreground service behind a run of the session's timers: a rest between sets, an EMOM or a hold. It holds
 * the CPU awake so the session's cues play with the screen off, and shows the run counting down as an ongoing
 * notification. It ends with the run, or when the session stops it. On Android 16 and later it is a Live Update,
 * as the workout's is: the time left in a chip in the status bar, and the run as a bar on the lock screen.
 * Special use, as the workout's is: a rest can run ten minutes and an EMOM an hour, past a short service's three.
 */
class TimerService : Service(), KoinComponent {
    private val log = Logger.withTag("TimerService")
    private val timers: ActiveTimers by inject()
    private val clock: WallClock by inject()
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
            createChannel(getString(Res.string.timer_channel), getString(Res.string.timer_channel_description))
        }
        // Not counted: each run acquires it afresh, and one release in onDestroy lets it go however many came.
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.apply { setReferenceCounted(false) }
    }

    // Each stop is for the commands before it: a run started after it (its start, a later id) keeps the service.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val endAt = intent?.getLongExtra(EXTRA_END_AT, 0L) ?: 0L
        val startedAt = intent?.getLongExtra(EXTRA_STARTED_AT, endAt) ?: endAt
        val kind = intent?.getStringExtra(EXTRA_KIND) ?: KIND_REST
        val minutes = intent?.getIntExtra(EXTRA_MINUTES, 0) ?: 0
        // Started for the foreground, so to the foreground first, whatever follows: stopping short of it
        // crashes the app.
        goForeground(endAt)
        if (endAt <= clock.nowMillis()) {
            stopSelf(startId)
        } else {
            countDown(Run(kind, startedAt, endAt, minutes))
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

    // Held to this run's end, each run afresh: runs back to back keep one service, and never outlast the lock.
    private fun countDown(run: Run) {
        val holdFor = run.endAt - clock.nowMillis() + LINGER_MILLIS + WAKE_LOCK_MARGIN_MILLIS
        runCatching { wakeLock?.acquire(holdFor) }
            .onFailure { log.w(it) { "Couldn't hold the wake lock" } }
        countdown?.cancel()
        countdown = scope.launch {
            while (clock.nowMillis() < run.endAt) {
                notifications?.notify(NOTIFICATION_ID, notification(run))
                delay(TICK_MILLIS)
            }
            // The session's timers play the cue as the run ends; a moment later this is over too.
            delay(LINGER_MILLIS)
            stopSelf()
        }
    }

    /** The user swiped the app away: its session's run goes with it, beeps and all. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        timers.stopAll()
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

    /**
     * What is running ("Resting", "EMOM · minute 2 of 10", "Holding") over the time left, silent and ongoing, on
     * the lock screen; tapping it opens the app. An EMOM's time left is its minute's, the others' the whole run's.
     * From Android 16 the time left is the status bar's chip, and the run one segment the tracker crosses as it
     * runs; below 16 the builder drops both, and the request to promote it.
     */
    private suspend fun notification(run: Run): Notification {
        val now = clock.nowMillis()
        val emom = run.takeIf { it.kind == KIND_EMOM && it.minutes > 0 }
            ?.let { EmomClock(it.minutes).at(now - it.startedAt, emptySet()) }
        val left = formatClock(emom?.secondsLeft ?: secondsLeft(run.endAt))
        val title = when {
            emom != null -> getString(Res.string.emom_title, emom.minute, emom.minutes)
            run.kind == KIND_HOLD -> getString(Res.string.hold_title)
            else -> getString(Res.string.rest_title)
        }
        val length = ceilSeconds(run.endAt - run.startedAt).coerceAtLeast(1)
        val bar = NotificationCompat.ProgressStyle()
            .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(length)))
            .setProgress((length - secondsLeft(run.endAt)).coerceIn(0, length))
        return builder()
            .setContentTitle(title)
            .setContentText(getString(Res.string.timer_left, left))
            .setShortCriticalText(left)
            .setRequestPromotedOngoing(true)
            .setStyle(bar)
            .build()
    }

    private fun secondsLeft(endAt: Long): Int = ceilSeconds(endAt - clock.nowMillis())

    private fun ceilSeconds(millis: Long): Int =
        ((millis.coerceAtLeast(0) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()

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

    /** A run as the service was handed it: its [kind] (one of the `KIND_`s), times and, for an EMOM, minutes. */
    private class Run(val kind: String, val startedAt: Long, val endAt: Long, val minutes: Int)

    companion object {
        internal const val EXTRA_KIND = "dev.justinsmith.lift.TIMER_KIND"
        internal const val EXTRA_STARTED_AT = "dev.justinsmith.lift.REST_STARTED_AT"
        internal const val EXTRA_END_AT = "dev.justinsmith.lift.REST_END_AT"
        internal const val EXTRA_MINUTES = "dev.justinsmith.lift.TIMER_MINUTES"
        internal const val ACTION_STOP = "dev.justinsmith.lift.REST_STOP"
        internal const val KIND_REST = "rest"
        internal const val KIND_EMOM = "emom"
        internal const val KIND_HOLD = "hold"

        /** The id the rests' channel had, so a user's settings for it carry over to every run's. */
        private const val CHANNEL_ID = "rest"

        /** Beside the workout's, which is 1. */
        private const val NOTIFICATION_ID = 2
        private const val REQUEST_OPEN = 10
        private const val WAKE_LOCK_TAG = "interval:timer"
        private const val TICK_MILLIS = 1_000L
        private const val MILLIS_PER_SECOND = 1_000L
        private const val LINGER_MILLIS = 1_500L

        /** How long past a run's end and linger the lock lasts, should the stop somehow not come by then. */
        private const val WAKE_LOCK_MARGIN_MILLIS = 5_000L
    }
}
