package dev.justinsmith.interval.platform

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** How often a screen left open checks whether the day has changed. */
const val DAY_CHECK_MILLIS = 60_000L

/**
 * Today's epoch day by [timestamps], at once and again whenever it changes, checked every [checkMillis]: what a
 * screen combines with its data so that its headings, its week and its "today" move on at midnight.
 */
fun WallClock.days(timestamps: TimestampFormatter, checkMillis: Long = DAY_CHECK_MILLIS): Flow<Long> = flow {
    while (true) {
        emit(timestamps.dayNumber(nowMillis()))
        delay(checkMillis)
    }
}.distinctUntilChanged()
