package dev.justinsmith.interval.ui

import androidx.compose.runtime.Composable
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.weekday_1
import dev.justinsmith.interval.resources.weekday_2
import dev.justinsmith.interval.resources.weekday_3
import dev.justinsmith.interval.resources.weekday_4
import dev.justinsmith.interval.resources.weekday_5
import dev.justinsmith.interval.resources.weekday_6
import dev.justinsmith.interval.resources.weekday_7
import dev.justinsmith.interval.resources.weekday_initial_1
import dev.justinsmith.interval.resources.weekday_initial_2
import dev.justinsmith.interval.resources.weekday_initial_3
import dev.justinsmith.interval.resources.weekday_initial_4
import dev.justinsmith.interval.resources.weekday_initial_5
import dev.justinsmith.interval.resources.weekday_initial_6
import dev.justinsmith.interval.resources.weekday_initial_7
import dev.justinsmith.interval.resources.weekday_short_1
import dev.justinsmith.interval.resources.weekday_short_2
import dev.justinsmith.interval.resources.weekday_short_3
import dev.justinsmith.interval.resources.weekday_short_4
import dev.justinsmith.interval.resources.weekday_short_5
import dev.justinsmith.interval.resources.weekday_short_6
import dev.justinsmith.interval.resources.weekday_short_7
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** How a weekday is written: "Monday", "Mon", or "M". */
enum class WeekdayForm { Name, Short, Initial }

/** A weekday, 1 (Monday) to 7 (Sunday) as the calendar maths counts them, in words. */
@Composable
fun weekdayText(weekday: Int, form: WeekdayForm = WeekdayForm.Name): String {
    val table = when (form) {
        WeekdayForm.Name -> WeekdayNames
        WeekdayForm.Short -> WeekdayShort
        WeekdayForm.Initial -> WeekdayInitials
    }
    return stringResource(table[weekday - 1])
}

private val WeekdayNames: List<StringResource> = listOf(
    Res.string.weekday_1,
    Res.string.weekday_2,
    Res.string.weekday_3,
    Res.string.weekday_4,
    Res.string.weekday_5,
    Res.string.weekday_6,
    Res.string.weekday_7,
)

private val WeekdayShort: List<StringResource> = listOf(
    Res.string.weekday_short_1,
    Res.string.weekday_short_2,
    Res.string.weekday_short_3,
    Res.string.weekday_short_4,
    Res.string.weekday_short_5,
    Res.string.weekday_short_6,
    Res.string.weekday_short_7,
)

private val WeekdayInitials: List<StringResource> = listOf(
    Res.string.weekday_initial_1,
    Res.string.weekday_initial_2,
    Res.string.weekday_initial_3,
    Res.string.weekday_initial_4,
    Res.string.weekday_initial_5,
    Res.string.weekday_initial_6,
    Res.string.weekday_initial_7,
)
