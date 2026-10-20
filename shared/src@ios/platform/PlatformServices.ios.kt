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
import dev.justinsmith.interval.timer.dateOfEpochDay
import dev.justinsmith.interval.timer.epochDayOf
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
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.autoupdatingCurrentLocale
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.Foundation.localeIdentifier
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

@Composable
actual fun rememberPlatformModule(): Module = remember {
    module {
        single<PlatformStorage> { IosStorage() }
        single<CuePlayer> { IosCuePlayer() }.withOptions {
            onClose { it?.release() }
        }
        single<TimestampFormatter> { FoundationTimestampFormatter() }
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

private const val NOON_HOUR = 12L

private class FoundationTimestampFormatter : TimestampFormatter {
    private var formats: Formats? = null

    override fun date(epochMillis: Long): String = formats().date.stringFromDate(nsDate(epochMillis))

    override fun time(epochMillis: Long): String = formats().time.stringFromDate(nsDate(epochMillis))

    // Gregorian, whichever calendar the user reads dates in, so the day count is the epoch day the rest of
    // the app (and the other platforms) use. Its time zone follows the device's as it changes.
    private val gregorian = requireNotNull(NSCalendar.calendarWithIdentifier(NSCalendarIdentifierGregorian)).apply {
        timeZone = NSTimeZone.localTimeZone
    }

    override fun dayNumber(epochMillis: Long): Long {
        val parts = gregorian.components(
            NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay,
            fromDate = nsDate(epochMillis),
        )
        return epochDayOf(parts.year.toInt(), parts.month.toInt(), parts.day.toInt())
    }

    override fun dateOfDay(epochDay: Long): String = formats().date.stringFromDate(noonOn(epochDay))

    override fun monthTitle(epochDay: Long): String = formats().month.stringFromDate(noonOn(epochDay))

    // Noon rather than midnight, which a daylight-saving change can skip.
    private fun noonOn(epochDay: Long): NSDate {
        val date = dateOfEpochDay(epochDay)
        val parts = NSDateComponents().apply {
            year = date.year.toLong()
            month = date.month.toLong()
            day = date.day.toLong()
            hour = NOON_HOUR
        }
        return requireNotNull(gregorian.dateFromComponents(parts))
    }

    private fun nsDate(epochMillis: Long): NSDate =
        NSDate.dateWithTimeIntervalSince1970(epochMillis.milliseconds.toDouble(DurationUnit.SECONDS))

    // A template is turned into a format once, for one locale: a region change (12 to 24 hours, say)
    // needs new formatters, not just a new locale on the old ones.
    private fun formats(): Formats {
        val locale = NSLocale.currentLocale.localeIdentifier
        return formats?.takeIf { it.locale == locale } ?: Formats(locale).also { formats = it }
    }

    // Templates rather than fixed formats, so each locale orders the parts and picks 12 or 24 hours its way.
    private class Formats(val locale: String) {
        val date = formatter("EEEEMMMd")
        val time = formatter("jmm")
        val month = formatter("yMMMM")

        private fun formatter(template: String) = NSDateFormatter().apply {
            setLocale(NSLocale.autoupdatingCurrentLocale)
            setTimeZone(NSTimeZone.localTimeZone)
            setLocalizedDateFormatFromTemplate(template)
        }
    }
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
