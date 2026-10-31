package dev.justinsmith.interval.fakes

import dev.justinsmith.interval.platform.AppInfo
import dev.justinsmith.interval.platform.ShareableFile
import dev.justinsmith.interval.platform.Sharer
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.timer.MINUTES_PER_HOUR
import dev.justinsmith.interval.timer.dateOfEpochDay
import dev.justinsmith.interval.timer.epochDayOf
import org.koin.core.module.Module
import org.koin.dsl.module

const val DAY_MILLIS = 24L * 60 * 60 * 1000
private const val MINUTE_MILLIS = 60L * 1000

/** The tests' today: Thursday 15 October 2026, as an epoch day. */
val TODAY: Long = epochDayOf(2026, 10, 15)

/** Midday of [epochDay] in epoch millis, well clear of either midnight. */
fun noonOf(epochDay: Long): Long = epochDay * DAY_MILLIS + DAY_MILLIS / 2

/**
 * Days are epoch days in UTC, as the platforms count them, so tests don't depend on a time zone. Day 0,
 * 1 January 1970, is a Thursday.
 */
object FixedTimestampFormatter : TimestampFormatter {
    override fun date(epochMillis: Long): String = dateOfDay(dayNumber(epochMillis))

    override fun time(epochMillis: Long): String = "at $epochMillis"

    override fun dayNumber(epochMillis: Long): Long = epochMillis.floorDiv(DAY_MILLIS)

    override fun dateOfDay(epochDay: Long): String = "day $epochDay"

    override fun monthTitle(epochDay: Long): String = dateOfEpochDay(epochDay).let { "${it.month}/${it.year}" }

    /** On the 24-hour clock, "7:30", "18:00". */
    override fun timeOfDay(minuteOfDay: Int): String =
        "${minuteOfDay / MINUTES_PER_HOUR}:${(minuteOfDay % MINUTES_PER_HOUR).toString().padStart(2, '0')}"

    override fun minuteOfDay(epochMillis: Long): Int = (epochMillis.mod(DAY_MILLIS) / MINUTE_MILLIS).toInt()
}

/** A build that calls itself [version]. */
object FakeAppInfo : AppInfo {
    override val version: String = "1.2.3"
}

/** Records what was offered to the system to share. */
class RecordingSharer : Sharer {
    val files = mutableListOf<ShareableFile>()
    val texts = mutableListOf<String>()

    override suspend fun shareFile(file: ShareableFile) {
        files += file
    }

    override fun shareText(text: String) {
        texts += text
    }
}

/**
 * Stands in for core's `rememberCorePlatformModule()`: a [clock] standing at 0 unless a test moves it, the
 * [FixedTimestampFormatter], [FakeAppInfo] and a [sharer] that records.
 */
fun fakeCorePlatformModule(
    clock: WallClock = WallClock { 0L },
    formatter: TimestampFormatter = FixedTimestampFormatter,
    sharer: Sharer = RecordingSharer(),
): Module = module {
    single { clock }
    single { formatter }
    single<AppInfo> { FakeAppInfo }
    single { sharer }
}
