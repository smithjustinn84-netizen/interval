@file:Suppress("detekt.MagicNumber")

package dev.justinsmith.interval.timer

/**
 * A day on the Gregorian calendar: [month] 1 for January to 12, [day] from 1. Days are counted as epoch
 * days, the number of days since 1 January 1970, which is what `TimestampFormatter.dayNumber` gives for
 * a moment in the device's time zone. Plans, history and the calendar all use that one count.
 */
data class CalendarDate(val year: Int, val month: Int, val day: Int) {
    /** This date as an epoch day. */
    val epochDay: Long get() = epochDayOf(year, month, day)
}

private const val DAYS_PER_ERA = 146_097L
private const val YEARS_PER_ERA = 400L
private const val MONTHS_PER_YEAR = 12

// 1 January 1970, counted from the 1 March of year 0 that the civil-days arithmetic starts on.
private const val EPOCH_SHIFT = 719_468L

// 1 January 1970 was a Thursday, day 4 of a week that starts on Monday.
private const val EPOCH_WEEKDAY_OFFSET = 3L

/** The epoch day of [year]-[month]-[day]. */
fun epochDayOf(year: Int, month: Int, day: Int): Long {
    // Howard Hinnant's days_from_civil: years start in March, so the leap day is the last of the year.
    val y = if (month <= 2) year - 1L else year.toLong()
    val era = y.floorDiv(YEARS_PER_ERA)
    val yearOfEra = y - era * YEARS_PER_ERA
    val shiftedMonth = (month + 9) % MONTHS_PER_YEAR
    val dayOfYear = (153 * shiftedMonth + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era * DAYS_PER_ERA + dayOfEra - EPOCH_SHIFT
}

/** The date [epochDay] falls on. */
fun dateOfEpochDay(epochDay: Long): CalendarDate {
    // civil_from_days, the inverse of [epochDayOf].
    val shifted = epochDay + EPOCH_SHIFT
    val era = shifted.floorDiv(DAYS_PER_ERA)
    val dayOfEra = shifted - era * DAYS_PER_ERA
    val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / (DAYS_PER_ERA - 1)) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val shiftedMonth = (5 * dayOfYear + 2) / 153
    val day = (dayOfYear - (153 * shiftedMonth + 2) / 5 + 1).toInt()
    val month = (if (shiftedMonth < 10) shiftedMonth + 3 else shiftedMonth - 9).toInt()
    val year = (yearOfEra + era * YEARS_PER_ERA + if (month <= 2) 1 else 0).toInt()
    return CalendarDate(year, month, day)
}

/** The day of the week [epochDay] falls on, 1 for Monday through 7 for Sunday. */
fun weekdayOf(epochDay: Long): Int = (epochDay + EPOCH_WEEKDAY_OFFSET).mod(DAYS_PER_WEEK) + 1

/** The Monday of the week [epochDay] is in. */
fun weekStartOf(epochDay: Long): Long = epochDay - (weekdayOf(epochDay) - 1)

/** The first day of the month [epochDay] is in. */
fun monthStartOf(epochDay: Long): Long {
    val date = dateOfEpochDay(epochDay)
    return epochDayOf(date.year, date.month, 1)
}

/** The first day of the month [months] after the one [epochDay] is in; negative counts back. */
fun monthStartAfter(epochDay: Long, months: Int): Long {
    val date = dateOfEpochDay(epochDay)
    val index = date.year * MONTHS_PER_YEAR + (date.month - 1) + months
    return epochDayOf(index.floorDiv(MONTHS_PER_YEAR), index.mod(MONTHS_PER_YEAR) + 1, 1)
}

/** Every day of the month [epochDay] is in, the first to the last. */
fun monthDaysOf(epochDay: Long): LongRange = monthStartOf(epochDay) until monthStartAfter(epochDay, 1)

/** How many days [month] of [year] has. */
fun daysInMonth(year: Int, month: Int): Int {
    val next = if (month == MONTHS_PER_YEAR) epochDayOf(year + 1, 1, 1) else epochDayOf(year, month + 1, 1)
    return (next - epochDayOf(year, month, 1)).toInt()
}
