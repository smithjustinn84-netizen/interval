@file:OptIn(ExperimentalForeignApi::class)

package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.justinsmith.core.sync.AppGroupSiblingFeed
import dev.justinsmith.core.sync.AppId
import dev.justinsmith.core.sync.SiblingFeed
import dev.justinsmith.interval.data.AppDatabase
import kotlinx.cinterop.ExperimentalForeignApi
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
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

@Composable
actual fun rememberPlatformModule(): Module = remember {
    module {
        single<PlatformStorage> { IosStorage() }
        single<CuePlayer> { IosCuePlayer() }.withOptions {
            onClose { it?.release() }
        }
        single<TimestampFormatter> { systemTimestampFormatter() }
        single<AppInfo> { IosAppInfo() }
        single<SiblingFeed> { AppGroupSiblingFeed(AppId.Interval) }
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
