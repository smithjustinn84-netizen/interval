package dev.justinsmith.interval.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.plan_lift_any
import dev.justinsmith.interval.history.resources.plan_lift_program_day
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import org.jetbrains.compose.resources.stringResource

/** What a plan is called: an interval workout by its name, a session on Lift by its kind. */
@Composable
internal fun planName(workout: PlanWorkout): String = when (workout) {
    is PlanWorkout.Interval -> workout.name
    PlanWorkout.Lift.ProgramDay -> stringResource(Res.string.plan_lift_program_day)
    PlanWorkout.Lift.AnySession -> stringResource(Res.string.plan_lift_any)
}

/** The colour a plan is marked in, as History's rows mark the two kinds: the work mark, or Lift's accent. */
@Composable
internal fun planMark(workout: PlanWorkout): Color = when (workout) {
    is PlanWorkout.Interval -> IntervalTheme.palette.workMark
    is PlanWorkout.Lift -> IntervalTheme.palette.accent
}

/** The icon beside a plan's name, so its kind reads by more than colour: the timer, or a kettlebell. */
internal fun planIcon(workout: PlanWorkout): ImageVector = when (workout) {
    is PlanWorkout.Interval -> IntervalIcons.Timer
    is PlanWorkout.Lift -> IntervalIcons.Bell
}
