@file:OptIn(ExperimentalForeignApi::class)

package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import dev.justinsmith.interval.timer.dateOfEpochDay
import dev.justinsmith.interval.timer.epochDayOf
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.autoupdatingCurrentLocale
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.Foundation.localeIdentifier
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIApplication
import platform.UIKit.UIStatusBarStyle
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.posix.CLOCK_MONOTONIC
import platform.posix.clock_gettime_nsec_np
import kotlin.time.AbstractLongTimeSource
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

// The idle timer is one app-wide flag, so one count for the app.
private val awakeHolds = AwakeHolds()

@Composable
actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        if (awakeHolds.acquire()) UIApplication.sharedApplication.idleTimerDisabled = true
        onDispose { if (awakeHolds.release()) UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}

@Composable
actual fun SystemBarIcons(lightIcons: Boolean) {
    val host = LocalUIViewController.current.parentViewController as? StatusBarHost
    SideEffect { host?.lightIcons = lightIcons }
}

/**
 * Hosts [content] and sets the status bar style for it, as [SystemBarIcons] asks. iOS takes the
 * style from the window's root controller, so this must be the root: SwiftUI's host would ignore it.
 * Light icons to begin with, matching the launch screen and the intro.
 */
class StatusBarHost(private val content: UIViewController) : UIViewController(nibName = null, bundle = null) {
    var lightIcons = true
        set(value) {
            if (field == value) return
            field = value
            setNeedsStatusBarAppearanceUpdate()
        }

    override fun preferredStatusBarStyle(): UIStatusBarStyle =
        if (lightIcons) UIStatusBarStyleLightContent else UIStatusBarStyleDarkContent

    override fun viewDidLoad() {
        super.viewDidLoad()
        addChildViewController(content)
        content.view.setFrame(view.bounds)
        content.view.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        view.addSubview(content.view)
        content.didMoveToParentViewController(this)
    }
}

@Composable
actual fun prefersReducedMotion(): Boolean = remember { UIAccessibilityIsReduceMotionEnabled() }

actual val materialBackPreview: Boolean = false

actual val platformPageTransitions: Boolean = true

actual fun epochMillisNow(): Long = NSDate().timeIntervalSince1970.seconds.inWholeMilliseconds

actual val realElapsedTime: TimeSource.WithComparableMarks get() = MonotonicThroughSleep

/**
 * Darwin's `CLOCK_MONOTONIC` keeps counting while the device sleeps (its `CLOCK_UPTIME_RAW`, behind
 * `mach_absolute_time`, does not), so a workout that ran on through a locked phone's sleep is right on waking.
 */
private object MonotonicThroughSleep : AbstractLongTimeSource(DurationUnit.NANOSECONDS) {
    override fun read(): Long = clock_gettime_nsec_np(CLOCK_MONOTONIC.toUInt()).toLong()
}

actual fun systemTimestampFormatter(): TimestampFormatter = FoundationTimestampFormatter()

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
