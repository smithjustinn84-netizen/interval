package dev.justinsmith.interval.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpSize
import dev.justinsmith.interval.history.UpNextItem
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.today.resources.Res
import dev.justinsmith.interval.today.resources.history_kind_conditioning
import dev.justinsmith.interval.today.resources.history_kind_mobility
import dev.justinsmith.interval.today.resources.history_kind_strength
import dev.justinsmith.interval.today.resources.history_up_next
import dev.justinsmith.interval.today.resources.home_resume_session
import dev.justinsmith.interval.today.resources.home_start_day
import dev.justinsmith.interval.today.resources.home_start_day_easier
import dev.justinsmith.interval.today.resources.home_start_session
import dev.justinsmith.interval.ui.IntervalSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.lift.domain.ProgramDay
import org.jetbrains.compose.resources.stringResource

/**
 * The room a step's mark takes: the wider of [IntervalSizes.stepNumber] and "00" as the number is set, so with
 * large text the digits stay on one line and every row's words still start level; and its height, the number's
 * or the done tick's, which the lane breaks round.
 */
@Composable
internal fun stepMarkSize(): DpSize {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val digits = measurer.measure(stepNumber(0), IntervalTheme.type.value).size
    return with(density) {
        DpSize(maxOf(IntervalSizes.stepNumber, digits.width.toDp()), maxOf(Sizes.icon, digits.height.toDp()))
    }
}

/** The lane down a step's mark: [above] it from the step before, [below] it to the next; null for none. */
internal class Lane(val above: Color?, val below: Color?)

/**
 * The step to do now's tag: what kind it is ("Up next · Strength"); a flow before a sport says which, and a sport's
 * day, named by its title, says when.
 */
@Composable
internal fun leadCaption(item: UpNextItem): String = when (item) {
    is UpNextItem.Flow -> upNextCaption(item)

    is UpNextItem.Lift -> stringResource(Res.string.history_up_next, stringResource(Res.string.history_kind_strength))

    is UpNextItem.Planned -> stringResource(
        Res.string.history_up_next,
        stringResource(
            if (item.config.activity == Activity.Mobility) {
                Res.string.history_kind_mobility
            } else {
                Res.string.history_kind_conditioning
            },
        ),
    )

    // A sport's name is its title already: its tag says when.
    is UpNextItem.SportDay -> upNextCaption(item)
}

/**
 * What a session's Start says: Resume, or the program's day by its number ([eased]: "Start day 3, easier"), or a
 * session of one's own.
 */
@Composable
internal fun liftStartLabel(item: UpNextItem.Lift, day: ProgramDay?, eased: Boolean): String = when {
    item.resume -> stringResource(Res.string.home_resume_session)
    day != null && eased -> stringResource(Res.string.home_start_day_easier, day.number)
    day != null -> stringResource(Res.string.home_start_day, day.number)
    else -> stringResource(Res.string.home_start_session)
}
