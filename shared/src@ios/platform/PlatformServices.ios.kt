@file:OptIn(ExperimentalForeignApi::class)

package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.room.Room
import androidx.room.RoomDatabase
import co.touchlab.kermit.Logger
import dev.justinsmith.interval.data.AppDatabase
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.notification_round
import dev.justinsmith.interval.resources.phase_done
import dev.justinsmith.interval.resources.phase_get_ready
import dev.justinsmith.interval.resources.phase_rest
import dev.justinsmith.interval.resources.phase_work
import dev.justinsmith.interval.timer.MILLIS_PER_SECOND
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.UpcomingChange
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.setActive
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

@Composable
actual fun rememberPlatformModule(): Module = remember {
    module {
        single<PlatformStorage> { IosStorage() }
        single<CuePlayer> { IosCuePlayer() }.withOptions {
            onClose { it?.release() }
        }
        single<TimestampFormatter> { systemTimestampFormatter() }
        single<AppInfo> { IosAppInfo() }
        single<BackgroundWorkout> { IosBackgroundWorkout() }
    }
}

/**
 * iOS suspends the app soon after it leaves the screen, so nothing of the workout runs there: its ticker
 * stops, and on return it counts the time away in one step ([realElapsedTime], [WorkoutEngine.tick]) and is
 * where it should be, with the one cue for where it landed. What the user hears in between is the system's
 * doing: as the app goes to the background with a workout running, this schedules one local notification
 * for each change of phase ahead ([WorkoutControls.upcomingChanges]), the phase as its title ("Work", "Rest",
 * "Done"), the round under it, with the default sound, which follows the ringer switch as the Ambient cues do.
 * iOS keeps 64 pending requests per app, so the furthest changes of a long workout go unscheduled; the user
 * is back long before. Every request is dropped when the app comes back to the screen, which will cue the
 * change itself, and when the workout pauses, ends or leaves. Notifications already shown are cleared then
 * too: back in the app, they are history.
 *
 * Not background audio: the Ambient session the cues use stops with the app, and the one way to stay alive is
 * the `audio` background mode with a Playback session that is always playing, which here would mean silence
 * between cues. Apple's review guideline 2.5.4 rejects that use of the mode, and the Playback category would
 * end the mixing with the user's music and the ringer switch's say over the cues that Ambient gives.
 *
 * Observes `UIApplication`'s own notifications rather than a scene's: the app has one scene, and these say
 * when the whole app is away.
 */
private class IosBackgroundWorkout : BackgroundWorkout {
    private val log = Logger.withTag("IosBackgroundWorkout")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val center: UNUserNotificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()

    /** The workout being kept, or null while there is none. */
    private var controls: WorkoutControls? = null

    /** What it last showed, for the round count and whether it is paused; null until the first update. */
    private var status: WorkoutStatus? = null

    /** Bumped by each scheduling and each clear, so a scheduling overtaken by a clear adds nothing after it. */
    private var generation = 0

    init {
        observe(UIApplicationDidEnterBackgroundNotification) { leftScreen() }
        observe(UIApplicationWillEnterForegroundNotification) { clear() }
    }

    /** Runs [action] on the main queue each time the app posts [name]; for the life of the process, like this. */
    private fun observe(name: String?, action: () -> Unit) {
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = name,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
            usingBlock = { action() },
        )
    }

    override fun start(controls: WorkoutControls) {
        this.controls = controls
        status = null
    }

    override fun update(status: WorkoutStatus) {
        if (status.paused && this.status?.paused == false) clear()
        this.status = status
    }

    override fun stop() {
        controls = null
        status = null
        clear()
    }

    /** The app is going to the background: the changes ahead, read now, become the system's to announce. */
    private fun leftScreen() {
        val rounds = status?.rounds ?: return
        // Empty while paused or over: nothing to announce.
        val changes = controls?.upcomingChanges()?.takeIf { it.isNotEmpty() } ?: return
        center.removeAllPendingNotificationRequests()
        val mine = ++generation
        scope.launch { schedule(changes.take(MAX_PENDING_REQUESTS), rounds, mine) }
    }

    /** Reading each string suspends, so the app may have come back midway: then the rest are not added. */
    private suspend fun schedule(changes: List<UpcomingChange>, rounds: Int, mine: Int) {
        for ((index, change) in changes.withIndex()) {
            if (mine != generation) return
            val content = UNMutableNotificationContent().apply {
                setTitle(getString(change.phase.title()))
                setBody(getString(Res.string.notification_round, change.round, rounds))
                setSound(UNNotificationSound.defaultSound)
            }
            // A trigger wants a positive interval; a change due this instant is announced at once.
            val seconds = (change.offsetMillis.toDouble() / MILLIS_PER_SECOND).coerceAtLeast(MIN_TRIGGER_SECONDS)
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
            val request = UNNotificationRequest.requestWithIdentifier("$REQUEST_PREFIX$index", content, trigger)
            center.addNotificationRequest(request) { error ->
                if (error != null) log.w { "Couldn't schedule ${change.phase} at ${change.offsetMillis} ms: $error" }
            }
        }
    }

    private fun clear() {
        generation++
        center.removeAllPendingNotificationRequests()
        center.removeAllDeliveredNotifications()
    }

    private fun Phase.title() = when (this) {
        Phase.Prep -> Res.string.phase_get_ready
        Phase.Work -> Res.string.phase_work
        Phase.Rest -> Res.string.phase_rest
        Phase.Done -> Res.string.phase_done
    }

    private companion object {
        /** iOS keeps this many pending requests per app and drops the rest. */
        const val MAX_PENDING_REQUESTS = 64
        const val MIN_TRIGGER_SECONDS = 0.1
        const val REQUEST_PREFIX = "workout-change-"
    }
}

/**
 * iOS: authorization to show notifications, asked for when a workout starts. The system puts the question to
 * the user once, and from then on answers from Settings without asking, so this asks each time rather than
 * keeping count. A refusal costs the notifications off screen only: the workout runs on either way.
 */
@Composable
actual fun AskToShowWorkoutOffScreen() {
    LaunchedEffect(Unit) {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { _, _ -> }
    }
}

/** The bundle's short version string: `MARKETING_VERSION` in the Xcode project. */
private class IosAppInfo : AppInfo {
    override val version: String? =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
}

/** Application Support rather than Documents, which the user can see when file sharing is on. */
private class IosStorage : PlatformStorage {
    private val directory: String = run {
        val url: NSURL? = NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
        requireNotNull(url?.path) { "No Application Support directory" }
    }

    override fun databaseBuilder(): RoomDatabase.Builder<AppDatabase> =
        Room.databaseBuilder<AppDatabase>(name = "$directory/$DATABASE_NAME")

    override val preferencesPath: String = "$directory/$PREFERENCES_NAME"
}

/**
 * System sounds for beeps, AVSpeechSynthesizer for voice, and the feedback generators for haptics. The
 * audio session is Ambient: cues mix over the user's music rather than stopping it, and voice follows
 * the ringer switch as the system-sound beeps do.
 */
private class IosCuePlayer : CuePlayer {
    private val synthesizer = AVSpeechSynthesizer()

    init {
        AVAudioSession.sharedInstance().run {
            setCategory(AVAudioSessionCategoryAmbient, error = null)
            setActive(true, error = null)
        }
    }

    override fun beep(beep: Beep) {
        val sound: UInt = when (beep) {
            Beep.Countdown -> 1103u
            Beep.Halfway -> 1104u
            Beep.Start -> 1113u
            Beep.Work -> 1005u
            Beep.Rest -> 1114u
            Beep.Finished -> 1025u
        }
        AudioServicesPlaySystemSound(sound)
    }

    private val speech = SpeechQueue { text ->
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        synthesizer.speakUtterance(AVSpeechUtterance(string = text))
    }

    override fun speak(speech: Speech) {
        this.speech.speak(speech)
    }

    // As on Android, each can be told apart without looking: one firm tap to work, a light one to rest,
    // and the success pattern at the end.
    override fun vibrate(haptic: Haptic) {
        when (haptic) {
            Haptic.Work -> UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy).impactOccurred()

            Haptic.Rest -> UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred()

            Haptic.Finished -> UINotificationFeedbackGenerator()
                .notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
        }
    }

    override fun release() {
        speech.close()
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }
}
