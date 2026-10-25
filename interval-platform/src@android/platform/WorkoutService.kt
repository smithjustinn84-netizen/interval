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
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString

/**
 * The foreground service behind a running workout. It keeps the process out of the cached state the system
 * freezes, holds the CPU awake so the cues play with the screen off, and shows the workout as an ongoing
 * notification: the clock, the phase and round under it, the phase colour, and Pause or Resume and End,
 * which act on the workout through [controls]. Its type is special use: a workout runs longer than a short
 * service's three minutes, and the health type wants sensor permissions the app has no use for.
 */
class WorkoutService : Service() {
    private val log = Logger.withTag("WorkoutService")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var showing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // The channel must exist before the first notification, which startForeground posts at once.
        // Reading two short strings from the app's resources is the one wait.
        runBlocking { createChannel() }
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
                getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(current))
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

    private suspend fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(Res.string.notification_channel_workout),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(Res.string.notification_channel_workout_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /**
     * The clock as the title, the phase and round under it, and the phase colour over the whole notification,
     * as it is over the screen's ring and band: the phase reads from across the room there, and from the lock
     * screen here.
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
        val color = when {
            status.paused -> PhaseColors.paused
            status.phase == Phase.Prep -> PhaseColors.prep
            status.phase == Phase.Work -> PhaseColors.work
            status.phase == Phase.Rest -> PhaseColors.rest
            else -> PhaseColors.done
        }
        val pauseLabel = getString(if (status.paused) Res.string.workout_resume else Res.string.workout_pause)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .common()
            .setContentTitle(status.clock)
            .setContentText(getString(Res.string.notification_workout_text, phase, status.round, status.rounds))
            .setColor(color.toArgb())
            .setColorized(true)
            .addAction(0, pauseLabel, serviceAction(ACTION_TOGGLE_PAUSE, REQUEST_TOGGLE_PAUSE))
            .addAction(0, getString(Res.string.workout_end), serviceAction(ACTION_END, REQUEST_END))
            .build()
    }

    /** What every version of the notification shares: silent, ongoing, on the lock screen, opens the app. */
    private fun NotificationCompat.Builder.common(): NotificationCompat.Builder = this
        .setSmallIcon(smallIcon())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_WORKOUT)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(openApp())

    /**
     * The app's own mark (`ic_notification`, generated with the launcher icons into the app's resources, so
     * found by name from here), or the system's alarm icon in a build without it.
     */
    private fun smallIcon(): Int {
        @Suppress("DiscouragedApi") // Once per notification, for one resource another module owns.
        val own = resources.getIdentifier(SMALL_ICON_NAME, "drawable", packageName)
        return if (own != 0) own else android.R.drawable.ic_lock_idle_alarm
    }

    private fun appName(): CharSequence = applicationInfo.loadLabel(packageManager)

    /** Brings the app's task forward, as tapping its launcher icon does, rather than opening a second one. */
    private fun openApp(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(this, REQUEST_OPEN, launch, PendingIntent.FLAG_IMMUTABLE)
    }

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
        private const val SMALL_ICON_NAME = "ic_notification"
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
