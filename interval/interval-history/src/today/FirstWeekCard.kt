package dev.justinsmith.interval.today

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.first_week_build
import dev.justinsmith.interval.history.resources.first_week_build_hint
import dev.justinsmith.interval.history.resources.first_week_done
import dev.justinsmith.interval.history.resources.first_week_flow
import dev.justinsmith.interval.history.resources.first_week_flow_hint
import dev.justinsmith.interval.history.resources.first_week_hide
import dev.justinsmith.interval.history.resources.first_week_sport
import dev.justinsmith.interval.history.resources.first_week_sport_hint
import dev.justinsmith.interval.history.resources.first_week_step_done
import dev.justinsmith.interval.history.resources.first_week_step_todo
import dev.justinsmith.interval.history.resources.first_week_title
import dev.justinsmith.interval.history.resources.first_week_workout
import dev.justinsmith.interval.history.resources.first_week_workout_pick
import dev.justinsmith.interval.history.resources.first_week_workout_start
import dev.justinsmith.interval.plan.sportName
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * The First week card: how far through it ("First week · 1 of 4") beside Hide, then a row a step, each ticked once
 * it is done. A step still to do says where it is done; the two that are done elsewhere lead there (Set up, Train's
 * Mobility pillar). With [leads], Today has a workout or a session up next with a Start, which is where the first
 * workout is; without, it is picked by time. Once every step is done the button reads Done, and the card stays
 * until tapped.
 */
@Composable
internal fun FirstWeekCard(week: FirstWeek, leads: Boolean, actions: HomeActions, modifier: Modifier = Modifier) {
    ListCard(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.space16, end = Spacing.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.first_week_title, week.done, week.steps.size),
                style = IntervalTheme.type.caption,
                color = IntervalTheme.palette.textDim,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            QuietTextButton(
                stringResource(if (week.finished) Res.string.first_week_done else Res.string.first_week_hide),
                actions.onHideFirstWeek,
            )
        }
        week.steps.forEachIndexed { index, step ->
            if (index > 0) RowDivider()
            StepRow(step, leads, actions)
        }
    }
}

/** A step: its tick, what it is over where it is done, and a chevron when the row leads there. */
@Composable
private fun StepRow(step: FirstWeekStep, leads: Boolean, actions: HomeActions, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val open = when (step.kind) {
        FirstWeekStep.Kind.Build -> actions.onSetup
        FirstWeekStep.Kind.Flow -> actions.onOpenMobility
        else -> null
    }.takeUnless { step.done }
    val state = stringResource(if (step.done) Res.string.first_week_step_done else Res.string.first_week_step_todo)
    Row(
        modifier
            .fillMaxWidth()
            .then(if (open != null) Modifier.clickable(onClick = open) else Modifier)
            .semantics(mergeDescendants = true) { stateDescription = state }
            .heightIn(min = Sizes.listRow)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Tick(step.done)
        Column(Modifier.weight(1f)) {
            Text(
                stepName(step),
                style = IntervalTheme.type.label,
                color = if (step.done) palette.textDim else palette.text,
            )
            // A step done says no more than its tick does, which is read out as its state.
            if (!step.done) Text(stepHint(step, leads), style = IntervalTheme.type.meta, color = palette.textDim)
        }
        if (open != null) {
            Icon(IntervalIcons.Chevron, null, tint = palette.textDim, modifier = Modifier.size(Sizes.chipIcon))
        }
    }
}

/** The tick: a ring while the step is to do, filled with the accent and checked once it is done. */
@Composable
private fun Tick(done: Boolean, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val fill by animateColorAsState(if (done) palette.accent else Color.Transparent, label = "tick")
    Box(
        modifier
            .size(Sizes.icon)
            .clip(CircleShape)
            .drawBehind { drawRect(fill) }
            .then(if (done) Modifier else Modifier.border(Sizes.border, palette.textDim, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(IntervalIcons.Check, null, tint = palette.onAccent, modifier = Modifier.size(Sizes.chipIcon))
        }
    }
}

@Composable
private fun stepName(step: FirstWeekStep): String = when (step.kind) {
    FirstWeekStep.Kind.Build -> stringResource(Res.string.first_week_build)
    FirstWeekStep.Kind.Workout -> stringResource(Res.string.first_week_workout)
    FirstWeekStep.Kind.Flow -> stringResource(Res.string.first_week_flow)
    FirstWeekStep.Kind.Sport -> stringResource(Res.string.first_week_sport, step.sport?.let { sportName(it) }.orEmpty())
}

@Composable
private fun stepHint(step: FirstWeekStep, leads: Boolean): String = stringResource(
    when (step.kind) {
        FirstWeekStep.Kind.Build -> Res.string.first_week_build_hint

        FirstWeekStep.Kind.Workout ->
            if (leads) Res.string.first_week_workout_start else Res.string.first_week_workout_pick

        FirstWeekStep.Kind.Flow -> Res.string.first_week_flow_hint

        FirstWeekStep.Kind.Sport -> Res.string.first_week_sport_hint
    },
)
