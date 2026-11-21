package dev.justinsmith.interval.history

import androidx.compose.runtime.Composable
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.history_hours_minutes
import dev.justinsmith.interval.history.resources.home_minutes
import dev.justinsmith.interval.timer.SECONDS_PER_HOUR
import dev.justinsmith.interval.timer.SECONDS_PER_MINUTE
import dev.justinsmith.interval.timer.formatClock
import org.jetbrains.compose.resources.stringResource

/**
 * Time spent over a week, a month or all of it, as a duration rather than a clock, which reads as a time of day:
 * minutes under an hour ("16 min"), hours and minutes from one ("2h 48m"), the clock under a minute ("0:40").
 */
@Composable
internal fun timeSpent(seconds: Int): String = when {
    seconds < SECONDS_PER_MINUTE -> formatClock(seconds)
    seconds < SECONDS_PER_HOUR -> stringResource(Res.string.home_minutes, seconds / SECONDS_PER_MINUTE)
    else -> longTotal(seconds)
}

/** A clock under an hour ("48:20"), hours and minutes from one ("9h 48m"). */
@Composable
internal fun longTotal(seconds: Int): String =
    if (seconds < SECONDS_PER_HOUR) {
        formatClock(seconds)
    } else {
        stringResource(
            Res.string.history_hours_minutes,
            seconds / SECONDS_PER_HOUR,
            seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE,
        )
    }
