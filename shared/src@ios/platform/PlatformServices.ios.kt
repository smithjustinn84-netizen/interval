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
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitEra
import platform.Foundation.NSCalendarUnitWeekday
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSUserDefaults
import platform.Foundation.dateByAddingTimeInterval
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

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

/** The status bar follows the system appearance. */
@Composable
actual fun SystemBarIcons(lightIcons: Boolean) = Unit

actual val materialBackPreview: Boolean = false

actual fun epochMillisNow(): Long = NSDate().timeIntervalSince1970.seconds.inWholeMilliseconds

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
    private val calendar = NSCalendar.currentCalendar
    private val dayFormat = NSDateFormatter().apply { dateFormat = "EEEE, MMM d" }
    private val timeFormat = NSDateFormatter().apply { dateFormat = "h:mm a" }

    override fun day(epochMillis: Long, nowMillis: Long): String {
        val date = date(epochMillis)
        val today = calendar.startOfDayForDate(date(nowMillis))
        val yesterday = today.dateByAddingTimeInterval(-SECONDS_PER_DAY)
        return when {
            calendar.isDate(date, inSameDayAsDate = today) -> "Today"
            calendar.isDate(date, inSameDayAsDate = yesterday) -> "Yesterday"
            else -> dayFormat.stringFromDate(date)
        }
    }

    override fun time(epochMillis: Long): String = timeFormat.stringFromDate(date(epochMillis))

    override fun dayNumber(epochMillis: Long): Long =
        calendar.ordinalityOfUnit(NSCalendarUnitDay, inUnit = NSCalendarUnitEra, forDate = date(epochMillis)).toLong()

    // Foundation counts Sunday as 1; the app counts Monday as 1.
    override fun weekday(epochMillis: Long): Int =
        (
            (calendar.component(NSCalendarUnitWeekday, fromDate = date(epochMillis)).toInt() + DAYS_PER_WEEK - 2) %
                DAYS_PER_WEEK
            ) +
            1

    private fun date(epochMillis: Long): NSDate =
        NSDate.dateWithTimeIntervalSince1970(epochMillis.milliseconds.toDouble(DurationUnit.SECONDS))

    private companion object {
        const val SECONDS_PER_DAY = 86_400.0
        const val DAYS_PER_WEEK = 7
    }
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
