package dev.justinsmith.interval.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import co.touchlab.kermit.Logger
import dev.justinsmith.interval.platform.resources.Res
import dev.justinsmith.interval.platform.resources.notification_channel_workout
import dev.justinsmith.interval.platform.resources.notification_channel_workout_description
import dev.justinsmith.interval.platform.resources.notification_workout_text
import dev.justinsmith.interval.platform.resources.phase_done
import dev.justinsmith.interval.platform.resources.phase_get_ready
import dev.justinsmith.interval.platform.resources.phase_rest
import dev.justinsmith.interval.platform.resources.phase_work
import dev.justinsmith.interval.platform.resources.workout_end
import dev.justinsmith.interval.platform.resources.workout_pause
import dev.justinsmith.interval.platform.resources.workout_paused
import dev.justinsmith.interval.platform.resources.workout_resume
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.ui.PhaseColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * The foreground service behind a running workout. It keeps the process out of the cached state the system
 * freezes, holds the CPU awake so the cues play with the screen off, and shows the workout as an ongoing
 * notification: the clock, the phase and round under it, the phase colour, and Pause or Resume and End,
 * which act on the workout through [controls]. On Android 16 and later it is a Live Update: the clock in a
 * chip in the status bar, and the whole workout as a bar on the lock screen and in the shade. Its type is
 * special use: a workout runs longer than a short service's three minutes, and the health type wants sensor
 * permissions the app has no use for.
 */
class WorkoutService : Service() {
    private val log = Logger.withTag("WorkoutService")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var showing = false

    // Looked up once, on first use: each is a call into the system (the package manager for the launch
    // intent, the activity manager for each pending intent, the resource table for the icon), and the
    // notification is built again on the main thread every second the workout runs.
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private val icon by lazy { notificationIcon() }
    private val openApp by lazy { openAppIntent(REQUEST_OPEN) }
    private val togglePauseIntent by lazy { serviceAction(ACTION_TOGGLE_PAUSE, REQUEST_TOGGLE_PAUSE) }
    private val endIntent by lazy { serviceAction(ACTION_END, REQUEST_END) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // The channel must exist before the first notification, which startForeground posts at once. Its
        // words suspend, and blocking the main thread for them would eat into startForeground's few seconds;
        // so the app's name holds the place on the very first run, and the words follow (a channel created
        // again is renamed, nothing else).
        if (notifications?.getNotificationChannel(CHANNEL_ID) == null) createChannel(appName(), null)
        scope.launch {
            createChannel(
                getString(Res.string.notification_channel_workout),
                getString(Res.string.notification_channel_workout_description),
            )
        }
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PAUSE -> controls?.togglePause()
            ACTION_END -> controls?.end()
            else -> if (!showing) show()
        }
        // A button pressed after the workout went: nothing to drive, nothing to show.
        if (controls == null) stopSelf()
        return START_NOT_STICKY
    }

    private fun show() {
        showing = true
        val first = NotificationCompat.Builder(this, CHANNEL_ID).common().setContentTitle(appName()).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, first)
        }
        // StateFlow hands over distinct values only, and the status changes once a second with the clock.
        scope.launch {
            status.filterNotNull().collect { current ->
                keepAwake(!current.paused)
                notifications?.notify(NOTIFICATION_ID, notification(current))
            }
        }
    }

    /** The user swiped the app away: its workout went with it. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        keepAwake(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /** Held while the workout runs, let go while it is paused: a paused timer has nothing to say. */
    private fun keepAwake(awake: Boolean) {
        val lock = wakeLock ?: return
        runCatching {
            if (awake && !lock.isHeld) lock.acquire(WAKE_LOCK_TIMEOUT_MILLIS)
            if (!awake && lock.isHeld) lock.release()
        }.onFailure { log.w(it) { "Couldn't change the wake lock" } }
    }

    private fun createChannel(name: CharSequence, description: String?) {
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
            this.description = description
            setShowBadge(false)
        }
        notifications?.createNotificationChannel(channel)
    }

    /**
     * The clock as the title, the phase and round under it, and the phase colour, as it is over the screen's
     * ring and band: the phase reads from across the room there, and from the lock screen here. Before Android
     * 16 the colour fills the whole notification; from 16 the system promotes only a notification that is not
     * colourised, so the colour is the bar's ([progressStyle]) and the clock is the status bar's chip. Below 16
     * the builder drops the style, the chip and the request, and the notification is as it was.
     */
    private suspend fun notification(status: WorkoutStatus): Notification {
        val phase = getString(
            when {
                status.paused -> Res.string.workout_paused
                status.phase == Phase.Prep -> Res.string.phase_get_ready
                status.phase == Phase.Work -> Res.string.phase_work
                status.phase == Phase.Rest -> Res.string.phase_rest
                else -> Res.string.phase_done
            },
        )
        val color = if (status.paused) PhaseColors.paused else phaseColor(status.phase)
        val pauseLabel = getString(if (status.paused) Res.string.workout_resume else Res.string.workout_pause)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .common()
            .setContentTitle(status.clock)
            .setContentText(getString(Res.string.notification_workout_text, phase, status.round, status.rounds))
            .setColor(color.toArgb())
            .setColorized(Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA)
            .setShortCriticalText(status.clock)
            .setRequestPromotedOngoing(true)
            .apply { StatusBar.of(status.outline)?.let { setStyle(progressStyle(it, color)) } }
            .addAction(0, pauseLabel, togglePauseIntent)
            .addAction(0, getString(Res.string.workout_end), endIntent)
            .build()
    }

    /**
     * The workout end to end, each interval in its phase's colour (or, past what the system draws apart, one
     * segment in [color]), a point where each round begins, and the tracker at the time gone.
     */
    private fun progressStyle(bar: StatusBar, color: Color): NotificationCompat.ProgressStyle =
        NotificationCompat.ProgressStyle()
            .setProgressSegments(
                bar.segments.map { segment ->
                    NotificationCompat.ProgressStyle.Segment(segment.length)
                        .setColor((segment.phase?.let(::phaseColor) ?: color).toArgb())
                },
            )
            .setProgressPoints(bar.points.map { NotificationCompat.ProgressStyle.Point(it) })
            .setProgress(bar.progress)

    /** What every version of the notification shares: silent, ongoing, on the lock screen, opens the app. */
    private fun NotificationCompat.Builder.common(): NotificationCompat.Builder = this
        .setSmallIcon(icon)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_WORKOUT)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(openApp)

    private fun phaseColor(phase: Phase): Color = when (phase) {
        Phase.Prep -> PhaseColors.prep
        Phase.Work -> PhaseColors.work
        Phase.Rest -> PhaseColors.rest
        Phase.Done -> PhaseColors.done
    }

    private fun appName(): CharSequence = applicationInfo.loadLabel(packageManager)

    private fun serviceAction(action: String, request: Int): PendingIntent {
        val intent = Intent(this, WorkoutService::class.java).setAction(action)
        return PendingIntent.getService(this, request, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        /** What the running workout shows, from the ViewModel; null while there is no workout. */
        internal val status = MutableStateFlow<WorkoutStatus?>(null)

        /** The running workout's controls, from the ViewModel; null while there is no workout. */
        internal var controls: WorkoutControls? = null

        private const val CHANNEL_ID = "workout"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_TOGGLE_PAUSE = "dev.justinsmith.interval.TOGGLE_PAUSE"
        private const val ACTION_END = "dev.justinsmith.interval.END"
        private const val REQUEST_OPEN = 0
        private const val REQUEST_TOGGLE_PAUSE = 1
        private const val REQUEST_END = 2
        private const val WAKE_LOCK_TAG = "interval:workout"

        /** Let go on its own after this long, should the workout somehow outlive its service's stop. */
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 4L * 60 * 60 * 1000
    }
}
