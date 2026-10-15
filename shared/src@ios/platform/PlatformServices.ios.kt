@file:OptIn(ExperimentalForeignApi::class)

package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import dev.justinsmith.interval.data.KeyValueStore
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSUserDefaults
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication

@Composable
actual fun rememberPlatformModule(): Module = remember {
    module {
        single<KeyValueStore> { UserDefaultsStore() }
        single<CuePlayer> { IosCuePlayer() }
        single<TimestampFormatter> { FoundationTimestampFormatter() }
    }
}

@Composable
actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        UIApplication.sharedApplication.idleTimerDisabled = true
        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}

actual fun epochMillisNow(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

private class UserDefaultsStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? = defaults.stringForKey(PREFIX + key)

    override fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = PREFIX + key)
    }

    private companion object {
        const val PREFIX = "interval."
    }
}

private class FoundationTimestampFormatter : TimestampFormatter {
    private val formatter = NSDateFormatter().apply { dateFormat = "EEE, MMM d, h:mm a" }

    override fun format(epochMillis: Long): String =
        formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0))
}

/**
 * System sounds for beeps (they follow the ringer switch), AVSpeechSynthesizer for voice, and the
 * system vibration sound for haptics.
 */
private class IosCuePlayer : CuePlayer {
    private val synthesizer = AVSpeechSynthesizer()

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

    override fun speak(text: String) {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        synthesizer.speakUtterance(AVSpeechUtterance(string = text))
    }

    override fun vibrate(haptic: Haptic) {
        AudioServicesPlaySystemSound(SYSTEM_SOUND_VIBRATE)
    }

    override fun release() {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }

    private companion object {
        /** `kSystemSoundID_Vibrate`. */
        const val SYSTEM_SOUND_VIBRATE: UInt = 4095u
    }
}
