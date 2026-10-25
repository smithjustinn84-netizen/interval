package dev.justinsmith.interval.fakes

import dev.justinsmith.interval.platform.AppInfo
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.timer.dateOfEpochDay
import dev.justinsmith.interval.timer.epochDayOf

const val DAY_MILLIS = 24L * 60 * 60 * 1000

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
}

/** A build that calls itself [version]. */
object FakeAppInfo : AppInfo {
    override val version: String = "1.2.3"
}
