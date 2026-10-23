package dev.justinsmith.interval.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.activity_kettlebell
import dev.justinsmith.interval.resources.history_calories_value
import dev.justinsmith.interval.resources.history_ended_early
import dev.justinsmith.interval.resources.history_planned
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StandardEasing
import dev.justinsmith.interval.ui.StripeMark
import dev.justinsmith.interval.ui.joined
import dev.justinsmith.interval.ui.pressScale
import dev.justinsmith.interval.ui.workoutDescription
import org.jetbrains.compose.resources.stringResource

/** A day's heading over its card of workouts, each opening its page; the one open beside the log shows it. */
@Composable
internal fun DaySection(
    section: HistorySection,
    actions: HistoryActions,
    selected: (HistoryItem) -> Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        GroupLabel(dayHeading(section.day))
        ListCard {
            section.items.forEachIndexed { index, item ->
                if (index > 0) RowDivider(Modifier.padding(start = Spacing.space20))
                HistoryRow(item, actions.onOpenWorkout, onOpenLift = actions.onOpenLift, selected = selected(item))
            }
        }
    }
}

/**
 * One finished workout, opening its page with a chevron saying so: an interval workout once stored, a
 * kettlebell session by [onOpenLift]. [selected] while its page is open beside the log, in the accent's tint.
 */
@Composable
internal fun HistoryRow(
    item: HistoryItem,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenLift: (String) -> Unit = {},
    selected: Boolean = false,
) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val fill by animateColorAsState(
        if (selected) palette.accentTint else Color.Transparent,
        tween(Motion.STATE_MILLIS, easing = StandardEasing),
        label = "selected",
    )
    val id = item.id
    val lift = item.liftUid
    val click: (() -> Unit)? = when {
        id != null -> ({ onOpen(id) })
        lift != null -> ({ onOpenLift(lift) })
        else -> null
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .background(fill)
            // Only the open row says so: a phone's log has no selection to read out.
            .then(if (selected) Modifier.semantics { this.selected = true } else Modifier)
            .then(
                if (click == null) {
                    Modifier
                } else {
                    Modifier
                        .clickable(
                            interaction,
                            LocalIndication.current,
                            role = Role.Button,
                            onClick = click,
                        )
                        .pressScale(interaction)
                },
            )
            .padding(start = Spacing.space16, end = Spacing.space12, top = Spacing.space12, bottom = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        // Kettlebell sessions take the accent, as Lift draws them; interval workouts, the work mark.
        StripeMark(if (lift == null) palette.workMark else palette.accent, height = Sizes.historyMarkHeight)
        Column(Modifier.weight(1f)) {
            val shape = workoutDescription(item.config)
            val detail = listOfNotNull(
                stringResource(Res.string.history_planned).takeIf { item.planned },
                if (lift ==
                    null
                ) {
                    shape.takeIf { item.presetName != null }
                } else {
                    stringResource(Res.string.activity_kettlebell)
                },
                stringResource(Res.string.history_ended_early).takeIf { item.endedEarly },
                item.calories?.let { stringResource(Res.string.history_calories_value, it) },
                item.time,
            )
            Text(item.presetName ?: shape, style = IntervalTheme.type.label, color = palette.text)
            Text(
                joined(detail),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
        }
        Text(item.total, style = IntervalTheme.type.value, color = palette.text)
        if (click != null) {
            Icon(IntervalIcons.Chevron, null, tint = palette.textDim, modifier = Modifier.size(Sizes.icon))
        }
    }
}
