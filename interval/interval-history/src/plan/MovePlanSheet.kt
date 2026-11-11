package dev.justinsmith.interval.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.move_plan_free
import dev.justinsmith.interval.history.resources.move_plan_title
import dev.justinsmith.interval.history.resources.move_plan_to
import dev.justinsmith.interval.history.resources.move_plan_today
import dev.justinsmith.interval.history.resources.notice_plan_moved
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.joined
import dev.justinsmith.interval.ui.weekdayText
import org.jetbrains.compose.resources.stringResource

/**
 * Moves one plan off its day: the week's days it can go to, each saying what it holds, the first free one picked,
 * and Move to. [onMove] takes the day and the line saying so ("Moved Tabata to Thursday").
 */
@Composable
fun MovePlanSheet(
    sheet: MoveSheet,
    today: Long,
    onMove: (toDay: Long, movedText: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = IntervalTheme.palette
    val name = planName(sheet.plan.workout)
    var picked by rememberSaveable { mutableStateOf(sheet.suggested) }
    val title = stringResource(Res.string.move_plan_title, name)
    IntervalSheet(title = title, onDismiss = onDismiss) { hide ->
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
            Text(
                title,
                style = IntervalTheme.type.title,
                color = palette.text,
                modifier = Modifier.semantics { heading() },
            )
            ListCard(Modifier.selectableGroup()) {
                sheet.days.forEachIndexed { index, day ->
                    if (index > 0) RowDivider()
                    PickRow(
                        name = if (day.epochDay == today) {
                            stringResource(Res.string.move_plan_today)
                        } else {
                            weekdayText(day.weekday)
                        },
                        detail = dayHolds(day),
                        selected = day.epochDay == picked,
                        onPick = { picked = day.epochDay },
                    )
                }
            }
            val to = sheet.days.firstOrNull { it.epochDay == picked }
            val dayName = to?.let { weekdayText(it.weekday) }.orEmpty()
            val moved = stringResource(Res.string.notice_plan_moved, name, dayName)
            AccentButton(
                stringResource(Res.string.move_plan_to, dayName),
                onClick = {
                    if (to != null) {
                        onMove(to.epochDay, moved)
                        hide()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = to != null,
            )
        }
    }
}

/** What [day] already holds: its sport and workouts planned, or nothing. */
@Composable
private fun dayHolds(day: MoveDay): String {
    if (day.free) return stringResource(Res.string.move_plan_free)
    val sport = day.sport?.let { sportTitle(it) }
    return joined(listOfNotNull(sport) + day.workouts.map { planName(it) })
}
