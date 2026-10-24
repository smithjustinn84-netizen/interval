package dev.justinsmith.interval.history

import dev.justinsmith.interval.platform.TimestampFormatter

/** The heading over one day: "Today", "Yesterday", "Tomorrow", or its date. */
sealed interface HistoryDay {
    data object Today : HistoryDay

    data object Yesterday : HistoryDay

    data object Tomorrow : HistoryDay

    /** Any other day, as the platform formats its date: "Sunday, Oct 11". */
    data class On(val date: String) : HistoryDay
}

/** A day's heading: Today, Yesterday or Tomorrow beside [today], else its date. */
fun TimestampFormatter.heading(day: Long, today: Long): HistoryDay = when (day) {
    today -> HistoryDay.Today
    today - 1 -> HistoryDay.Yesterday
    today + 1 -> HistoryDay.Tomorrow
    else -> HistoryDay.On(dateOfDay(day))
}
