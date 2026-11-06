@file:OptIn(ExperimentalForeignApi::class)

package dev.justinsmith.interval.platform

import androidx.room.Room
import androidx.room.RoomDatabase
import co.touchlab.kermit.Logger
import dev.justinsmith.interval.data.AppDatabase
import dev.justinsmith.interval.platform.resources.Res
import dev.justinsmith.interval.platform.resources.notification_block_round
import dev.justinsmith.interval.platform.resources.notification_round
import dev.justinsmith.interval.platform.resources.notification_set_round
import dev.justinsmith.interval.platform.resources.phase_block_rest
import dev.justinsmith.interval.platform.resources.phase_cool_down
import dev.justinsmith.interval.platform.resources.phase_done
import dev.justinsmith.interval.platform.resources.phase_get_ready
import dev.justinsmith.interval.platform.resources.phase_rest
import dev.justinsmith.interval.platform.resources.phase_set_rest
import dev.justinsmith.interval.platform.resources.phase_warm_up
import dev.justinsmith.interval.platform.resources.phase_work
import dev.justinsmith.interval.timer.MILLIS_PER_SECOND
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.UpcomingChange
import dev.justinsmith.interval.timer.stepFor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
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
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

/** The interval side's services on iOS. */
fun platformModule(): Module = module {
    single<PlatformStorage> { IosStorage() }
    single<CuePlayer> { IosCuePlayer() }.withOptions {
        onClose { it?.release() }
    }
    // Lazily: the workout it reads is the workout feature's session, which takes this keeper in turn.
    single<BackgroundWorkout> { IosBackgroundWorkout(lazy { get<ActiveWorkout>() }) }.withOptions {
        onClose { (it as? IosBackgroundWorkout)?.release() }
    }
    single<PlanReminders> { IosPlanReminders(get()) }
}

/**
 * iOS suspends the app soon after it leaves the screen, so nothing of the workout runs there: its ticker
 * stops, and on return it counts the time away in one step ([realElapsedTime], [WorkoutEngine.tick]) and is
 * where it should be, with the one cue for where it landed. What the user hears in between is the system's
 * doing: as the app goes to the background with a workout running, this schedules one local notification
 * for each change of phase ahead ([ActiveWorkout.upcomingChanges]), the phase as its title ("Work", "Rest",
 * "Done"), the round under it, with the default sound, which follows the ringer switch as the Ambient cues do.
 * iOS keeps 64 pending requests per app, [PLAN_REMINDER_DAYS] of them the plan's reminders', so the furthest
 * changes of a long workout go unscheduled; the user is back long before. Every request of the workout's is
 * dropped (by its own identifiers, leaving the reminders) when the app comes back to the screen, which will
 * cue the change itself, and when the workout pauses, ends or leaves. Notifications already shown are cleared
 * then too: back in the app, they are history.
 *
 * Not background audio: the Ambient session the cues use stops with the app, and the one way to stay alive is
 * the `audio` background mode with a Playback session that is always playing, which here would mean silence
 * between cues. Apple's review guideline 2.5.4 rejects that use of the mode, and the Playback category would
 * end the mixing with the user's music and the ringer switch's say over the cues that Ambient gives.
 *
 * Observes `UIApplication`'s own notifications rather than a scene's: the app has one scene, and these say
 * when the whole app is away.
 */
private class IosBackgroundWorkout(private val active: Lazy<ActiveWorkout>) : BackgroundWorkout {
    private val log = Logger.withTag("IosBackgroundWorkout")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val center: UNUserNotificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()

    /** Follows the kept workout's status from [start] to [stop]. */
    private var following: Job? = null

    /** What it last showed, for the round count and whether it is paused; null while no workout is kept. */
    private var status: WorkoutStatus? = null

    /** Bumped by each scheduling and each clear, so a scheduling overtaken by a clear adds nothing after it. */
    private var generation = 0

    private val observers = listOf(
        observe(UIApplicationDidEnterBackgroundNotification) { leftScreen() },
        observe(UIApplicationWillEnterForegroundNotification) { clear() },
    )

    /** Runs [action] on the main queue each time the app posts [name], until [release]. */
    private fun observe(name: String?, action: () -> Unit) = NSNotificationCenter.defaultCenter.addObserverForName(
        name = name,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
        usingBlock = { action() },
    )

    /** The container is closing: stop listening, and drop any scheduling still under way. */
    fun release() {
        observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        scope.cancel()
    }

    override fun start() {
        following?.cancel()
        status = null
        following = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            active.value.status.filterNotNull().collect { now ->
                if (now.paused && status?.paused == false) clear()
                status = now
            }
        }
    }

    override fun stop() {
        following?.cancel()
        following = null
        status = null
        clear()
    }

    /** The app is going to the background: the changes ahead, read now, become the system's to announce. */
    private fun leftScreen() {
        val shown = status ?: return
        // Empty while paused or over: nothing to announce.
        val changes = active.value.upcomingChanges().takeIf { it.isNotEmpty() } ?: return
        center.removePendingNotificationRequestsWithIdentifiers(identifiers)
        val mine = ++generation
        scope.launch { schedule(changes.take(WORKOUT_REQUESTS), shown, mine) }
    }

    /**
     * Reading each string suspends, so the app may have come back midway: then the rest are not added. Each change
     * counts the rounds and sets of its own block, from [shown]'s blocks in a workout of blocks.
     */
    private suspend fun schedule(changes: List<UpcomingChange>, shown: WorkoutStatus, mine: Int) {
        for ((index, change) in changes.withIndex()) {
            if (mine != generation) return
            // A flow's change is its move, and where it is among them; its end has no more to say.
            val flow = shown.flow
            val step = flow?.stepFor(change.phase, change.block, change.round)
            val title = step?.let { flowTitle(change.phase, it) } ?: getString(title(change, shown))
            val body = if (flow == null) body(change, shown) else step?.let { flowCount(it) }
            val content = UNMutableNotificationContent().apply {
                setTitle(title)
                body?.let(::setBody)
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

    /** The phase [change] starts: a set rest after a block's last set, a block after it, is the rest between blocks. */
    private fun title(change: UpcomingChange, shown: WorkoutStatus): StringResource {
        val block = shown.blocks.getOrNull(change.block)
        val blockRest = block != null && change.phase == Phase.SetRest && change.set >= block.sets &&
            change.block < shown.blocks.lastIndex
        return if (blockRest) Res.string.phase_block_rest else change.phase.title()
    }

    /**
     * Its round, of the rounds and sets of its own block ([shown]'s blocks in a workout of blocks, with the block
     * first); none in the warm-up and cool-down, outside the rounds.
     */
    private suspend fun body(change: UpcomingChange, shown: WorkoutStatus): String? {
        if (change.phase == Phase.WarmUp || change.phase == Phase.CoolDown) return null
        val block = shown.blocks.getOrNull(change.block)
        val rounds = block?.rounds ?: shown.rounds
        val sets = block?.sets ?: shown.sets
        val round = if (sets > 1) {
            getString(Res.string.notification_set_round, change.set, sets, change.round, rounds)
        } else {
            getString(Res.string.notification_round, change.round, rounds)
        }
        if (block == null) return round
        return getString(Res.string.notification_block_round, change.block + 1, shown.blocks.size, round)
    }

    private fun clear() {
        generation++
        center.removePendingNotificationRequestsWithIdentifiers(identifiers)
        center.removeDeliveredNotificationsWithIdentifiers(identifiers)
    }

    private fun Phase.title() = when (this) {
        Phase.Prep -> Res.string.phase_get_ready
        Phase.WarmUp -> Res.string.phase_warm_up
        Phase.Work -> Res.string.phase_work
        Phase.Rest -> Res.string.phase_rest
        Phase.SetRest -> Res.string.phase_set_rest
        Phase.CoolDown -> Res.string.phase_cool_down
        Phase.Done -> Res.string.phase_done
    }

    private companion object {
        /** iOS keeps this many pending requests per app and drops the rest. */
        const val MAX_PENDING_REQUESTS = 64

        /** The workout's share of them, the plan's reminders keeping the rest. */
        const val WORKOUT_REQUESTS = MAX_PENDING_REQUESTS - PLAN_REMINDER_DAYS
        const val MIN_TRIGGER_SECONDS = 0.1
        const val REQUEST_PREFIX = "workout-change-"

        /** Every identifier the workout may have scheduled; removing one that was never added does nothing. */
        val identifiers: List<String> = (0 until WORKOUT_REQUESTS).map { "$REQUEST_PREFIX$it" }
    }
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
