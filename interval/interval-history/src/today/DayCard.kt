package dev.justinsmith.interval.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.justinsmith.interval.history.StartPill
import dev.justinsmith.interval.history.UpNextItem
import dev.justinsmith.interval.history.UpNextLater
import dev.justinsmith.interval.history.dayHeading
import dev.justinsmith.interval.history.preview
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.common_joined
import dev.justinsmith.interval.history.resources.history_kind_conditioning
import dev.justinsmith.interval.history.resources.history_kind_mobility
import dev.justinsmith.interval.history.resources.history_kind_strength
import dev.justinsmith.interval.history.resources.history_lift_any
import dev.justinsmith.interval.history.resources.history_lift_session
import dev.justinsmith.interval.history.resources.history_start
import dev.justinsmith.interval.history.resources.history_up_next
import dev.justinsmith.interval.history.resources.home_do_it_now
import dev.justinsmith.interval.history.resources.home_do_it_today
import dev.justinsmith.interval.history.resources.home_done_cd
import dev.justinsmith.interval.history.resources.home_done_logged
import dev.justinsmith.interval.history.resources.home_done_today
import dev.justinsmith.interval.history.resources.home_easier_title
import dev.justinsmith.interval.history.resources.home_easier_what
import dev.justinsmith.interval.history.resources.home_rest_move
import dev.justinsmith.interval.history.resources.home_rest_title
import dev.justinsmith.interval.history.resources.home_rest_until
import dev.justinsmith.interval.history.resources.home_resume_session
import dev.justinsmith.interval.history.resources.home_spare
import dev.justinsmith.interval.history.resources.home_start_day
import dev.justinsmith.interval.history.resources.home_start_day_easier
import dev.justinsmith.interval.history.resources.home_start_session
import dev.justinsmith.interval.history.resources.notice_sport_logged
import dev.justinsmith.interval.history.resources.preview_open_cd
import dev.justinsmith.interval.history.resources.sport_counts
import dev.justinsmith.interval.history.resources.sport_log_it
import dev.justinsmith.interval.history.shortDay
import dev.justinsmith.interval.history.upNextFigure
import dev.justinsmith.interval.history.upNextMeta
import dev.justinsmith.interval.history.upNextName
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.plan.sportTitle
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.restDayFlow
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StartButton
import dev.justinsmith.interval.ui.ToggleRow
import dev.justinsmith.interval.ui.joined
import dev.justinsmith.interval.ui.workoutDescription
import dev.justinsmith.lift.ui.estimateLine
import dev.justinsmith.lift.ui.programDayLine
import org.jetbrains.compose.resources.stringResource

// The day on Today, as a coach would lay it out: what is done ticked off, what is still to do in order under the
// one raised Start, and what can wait behind a quiet button.

/**
 * Today in order: what is done, ticked, then each step still to do, numbered where there is more than one thing
 * to the day. The first step is the one to do now: what it is, opening a look at it, over its Start (the tab's one
 * raised button), its Log it for a sport, and on a taxed day ([easier]) the easier plan to start the program's day
 * with. The steps after it are rows with a quiet button each; the program's day stood back behind a class says it
 * is for spare time. Under them, the one thing after today.
 */
@Composable
internal fun DayCard(
    plan: TodayPlan,
    easier: Boolean,
    actions: HomeActions,
    onLook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val numbered = plan.done.size + plan.steps.size > 1
    DayColumn(modifier) {
        plan.done.forEach { DoneRow(it, rail = numbered, last = false) }
        plan.steps.forEachIndexed { index, item ->
            val last = index == plan.steps.lastIndex
            val number = (plan.done.size + index + 1).takeIf { numbered }
            if (index == 0) {
                LeadStep(item, number, last, easier, actions, onLook)
            } else {
                LaterStep(item, number, last, plan.spare(item), actions)
            }
        }
        UpNextLater(
            listOfNotNull(plan.next) + plan.later,
            Modifier.padding(top = Spacing.space12),
            count = 1,
        )
    }
}

/** Everything today asked for, done: the ticks under "Done for today". */
@Composable
internal fun DoneCard(done: List<DoneStep>, modifier: Modifier = Modifier) {
    DayColumn(modifier) {
        Text(
            stringResource(Res.string.home_done_today),
            style = IntervalTheme.type.value,
            color = IntervalTheme.palette.text,
            modifier = Modifier.padding(bottom = Spacing.space12).semantics { heading() },
        )
        done.forEachIndexed { index, step -> DoneRow(step, rail = true, last = index == done.lastIndex) }
    }
}

/**
 * A day with nothing asked of it: it says so, and until when, and offers the one flow worth doing on a day off
 * behind a quiet Start.
 */
@Composable
internal fun RestCard(next: UpNextItem, actions: HomeActions, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val flow = restDayFlow
    ListCard(modifier) {
        Column(
            Modifier.padding(Spacing.space16),
            verticalArrangement = Arrangement.spacedBy(Spacing.space4),
        ) {
            Text(
                stringResource(Res.string.home_rest_title),
                style = IntervalTheme.type.value,
                color = palette.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(Res.string.home_rest_until, shortDay(next.day, next.epochDay, midSentence = true)),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
        }
        RowDivider()
        QuietRow(
            caption = stringResource(Res.string.home_rest_move),
            name = flow.name,
            detail = joined(listOfNotNull(workoutDescription(flow.config), flow.note)),
            modifier = Modifier.padding(Spacing.space16),
        ) { StartPill(primary = false, onClick = { actions.onStart(flow.name, flow.config) }) }
    }
}

/**
 * What comes after today, with nothing left to today: named under its day, opening a look at it, with a quiet
 * button to do it today all the same, and the one thing after it. The program's day stood back behind a class
 * already logged ([waiting]) is today's still, for spare time. A sport's day has no button: it is logged on its day.
 */
@Composable
internal fun NextCard(
    next: UpNextItem,
    later: List<UpNextItem>,
    waiting: Boolean,
    actions: HomeActions,
    onLook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DayColumn(modifier) {
        val look = next.preview()?.let { onLook }
        val described = stringResource(Res.string.preview_open_cd, upNextName(next))
        QuietRow(
            caption = if (waiting) {
                stringResource(Res.string.home_spare)
            } else {
                stringResource(Res.string.history_up_next, dayHeading(next.day))
            },
            name = upNextName(next),
            detail = joined(listOfNotNull(upNextMeta(next), upNextFigure(next))),
            onClick = look,
            onClickLabel = described,
        ) {
            val label = stringResource(if (waiting) Res.string.home_do_it_now else Res.string.home_do_it_today)
            when (next) {
                is UpNextItem.Planned ->
                    StartPill(primary = false, onClick = { actions.onStart(next.name, next.config) }, label = label)

                is UpNextItem.Flow -> StartPill(
                    primary = false,
                    onClick = { actions.onStart(next.flow.name, next.flow.config) },
                    label = label,
                )

                is UpNextItem.Lift -> StartPill(
                    primary = false,
                    onClick = { actions.onStartLift(next.programDay?.program?.key.takeUnless { next.resume }) },
                    label = label,
                )

                is UpNextItem.SportDay -> Unit
            }
        }
        UpNextLater(later, Modifier.padding(top = Spacing.space12), count = 1)
    }
}

/** The lead's own Log it, under a class that leads the day. */
@Composable
internal fun LogSportButton(sport: Sport, actions: HomeActions, modifier: Modifier = Modifier) {
    AccentButton(stringResource(Res.string.sport_log_it), logSport(sport, actions), modifier)
}

/** Logging [sport] today, with the words the notice says it in: "BJJ class logged". */
@Composable
private fun logSport(sport: Sport, actions: HomeActions): () -> Unit {
    val logged = stringResource(Res.string.notice_sport_logged, sportTitle(sport))
    return { actions.onLogSport(sport, logged) }
}

/** The card the day is laid out on. */
@Composable
private fun DayColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Corners.radius18)
            .background(IntervalTheme.palette.surface)
            .padding(Spacing.space16),
        content = content,
    )
}

/**
 * The step to do now: its caption, name and what it is, opening a look at it; on a taxed day the easier plan, on
 * unless switched off; and its button, which names no more than the card does: "Start", "Start day 3", "Start day
 * 3, easier", "Resume session", "Log it".
 */
@Composable
private fun LeadStep(
    item: UpNextItem,
    number: Int?,
    last: Boolean,
    easier: Boolean,
    actions: HomeActions,
    onLook: () -> Unit,
) {
    val palette = IntervalTheme.palette
    // The easier plan is Lift's to offer for the program's day, before it starts.
    val day = (item as? UpNextItem.Lift)?.takeUnless { it.resume }?.programDay
    val offered = easier && day != null
    var eased by rememberSaveable { mutableStateOf(true) }
    Step(number?.let { StepState.Now(it) }, last) {
        val look = item.preview()?.let { onLook }
        val described = stringResource(Res.string.preview_open_cd, upNextTitle(item))
        Column(
            Modifier
                .fillMaxWidth()
                .then(
                    if (look != null) {
                        Modifier.clickable(onClickLabel = described, onClick = look)
                    } else {
                        Modifier
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.space4),
        ) {
            Text(upNextCaption(item), style = IntervalTheme.type.caption, color = palette.textDim)
            Text(upNextTitle(item), style = IntervalTheme.type.value, color = palette.text)
            Text(upNextDetail(item), style = IntervalTheme.type.meta, color = palette.textDim)
        }
        if (offered) {
            ListCard(Modifier.padding(top = Spacing.space8), fill = palette.surfaceVariant) {
                ToggleRow(
                    stringResource(Res.string.home_easier_title),
                    stringResource(Res.string.home_easier_what),
                    checked = eased,
                    onToggle = { eased = !eased },
                )
            }
        }
        val button = Modifier.fillMaxWidth().padding(top = Spacing.space12)
        val start = stringResource(Res.string.history_start)
        when (item) {
            is UpNextItem.Planned -> StartButton({ actions.onStart(item.name, item.config) }, button, label = start)

            is UpNextItem.Lift -> {
                val program = item.programDay?.program?.key
                StartButton(
                    {
                        if (offered && eased && program != null) {
                            actions.onStartLiftEasier(program)
                        } else {
                            actions.onStartLift(program.takeUnless { item.resume })
                        }
                    },
                    button,
                    label = when {
                        item.resume -> stringResource(Res.string.home_resume_session)
                        day != null && offered && eased -> stringResource(Res.string.home_start_day_easier, day.number)
                        day != null -> stringResource(Res.string.home_start_day, day.number)
                        else -> stringResource(Res.string.home_start_session)
                    },
                )
            }

            is UpNextItem.SportDay -> LogSportButton(item.sport, actions, button)

            // Started as any workout is, and known for the flow it is by its shape.
            is UpNextItem.Flow ->
                StartButton({ actions.onStart(item.flow.name, item.flow.config) }, button, label = start)
        }
    }
}

/**
 * A step after the first: its name over what it is, with a quiet button of its own. The program's day stood back
 * behind a class ([spare]) says it is for spare time, and is done now from "Do it now".
 */
@Composable
private fun LaterStep(item: UpNextItem, number: Int?, last: Boolean, spare: Boolean, actions: HomeActions) {
    Step(number?.let { StepState.Later(it) }, last) {
        QuietRow(
            caption = stringResource(Res.string.home_spare).takeIf { spare },
            name = upNextName(item),
            detail = when (item) {
                is UpNextItem.SportDay -> stringResource(Res.string.sport_counts)

                is UpNextItem.Lift -> item.programDay?.let { day ->
                    joined(listOfNotNull(item.minutes?.let { estimateLine(it) }, programDayLine(day)))
                } ?: stringResource(Res.string.history_lift_any)

                is UpNextItem.Planned -> stringResource(
                    Res.string.common_joined,
                    workoutDescription(item.config),
                    formatClock(item.config.totalSeconds),
                )

                is UpNextItem.Flow -> workoutDescription(item.flow.config)
            },
        ) {
            when (item) {
                is UpNextItem.SportDay -> StartPill(
                    primary = false,
                    onClick = logSport(item.sport, actions),
                    label = stringResource(Res.string.sport_log_it),
                )

                is UpNextItem.Lift -> StartPill(
                    primary = false,
                    onClick = { actions.onStartLift(item.programDay?.program?.key.takeUnless { item.resume }) },
                    label = stringResource(
                        when {
                            item.resume -> Res.string.home_resume_session
                            spare -> Res.string.home_do_it_now
                            else -> Res.string.history_start
                        },
                    ),
                )

                is UpNextItem.Planned ->
                    StartPill(primary = false, onClick = { actions.onStart(item.name, item.config) })

                is UpNextItem.Flow ->
                    StartPill(primary = false, onClick = { actions.onStart(item.flow.name, item.flow.config) })
            }
        }
    }
}

/** Something done today: its tick, its name over its kind, and how long it ran. Read as one row, done. */
@Composable
private fun DoneRow(step: DoneStep, rail: Boolean, last: Boolean) {
    val palette = IntervalTheme.palette
    val done = stringResource(Res.string.home_done_cd)
    val name = when (step) {
        is DoneStep.Workout -> step.name ?: workoutDescription(step.config)
        is DoneStep.Session -> step.title.ifBlank { stringResource(Res.string.history_lift_session) }
        is DoneStep.SportLogged -> sportTitle(step.sport)
    }
    val detail = when (step) {
        is DoneStep.Workout -> stringResource(
            if (step.config.activity == Activity.Mobility) {
                Res.string.history_kind_mobility
            } else {
                Res.string.history_kind_conditioning
            },
        )

        is DoneStep.Session -> stringResource(Res.string.history_kind_strength)

        is DoneStep.SportLogged -> joined(
            listOf(stringResource(Res.string.home_done_logged), stringResource(Res.string.sport_counts)),
        )
    }
    val total = when (step) {
        is DoneStep.Workout -> formatClock(step.elapsedSeconds)
        is DoneStep.Session -> formatClock(step.elapsedSeconds)
        is DoneStep.SportLogged -> null
    }
    Step(
        StepState.Done.takeIf { rail },
        last,
        Modifier.semantics(mergeDescendants = true) { stateDescription = done },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = IntervalTheme.type.label, color = palette.text)
                Text(detail, style = IntervalTheme.type.meta, color = palette.textDim)
            }
            total?.let { Text(it, style = IntervalTheme.type.value, color = palette.text) }
        }
    }
}

/**
 * A quiet row: an optional [caption] over a [name] and a line of [detail], which open a look where [onClick] is
 * given, and the row's own [button] at its end.
 */
@Composable
private fun QuietRow(
    caption: String?,
    name: String,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    button: @Composable () -> Unit,
) {
    val palette = IntervalTheme.palette
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Column(
            Modifier
                .weight(1f)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
        ) {
            caption?.let { Text(it, style = IntervalTheme.type.caption, color = palette.textDim) }
            Text(name, style = IntervalTheme.type.label, color = palette.text)
            Text(detail, style = IntervalTheme.type.meta, color = palette.textDim)
        }
        button()
    }
}

/** How a step's dot is drawn: ticked, the one to do now, or one for later; the last two by their number. */
private sealed interface StepState {
    data object Done : StepState

    data class Now(val number: Int) : StepState

    data class Later(val number: Int) : StepState
}

/**
 * One step of the day: its dot at the start, joined to the next step's by a line down the card unless it is the
 * [last], and its content beside it. With no [state] the day has one thing to it, and the step is its content alone.
 */
@Composable
private fun Step(
    state: StepState?,
    last: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val line = IntervalTheme.palette.line
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (state != null && !last) {
                    Modifier.drawBehind {
                        val x = Sizes.icon.toPx() / 2
                        drawLine(
                            line,
                            Offset(x, Sizes.icon.toPx() + Spacing.space4.toPx()),
                            Offset(x, size.height - Spacing.space4.toPx()),
                            strokeWidth = Sizes.border.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        state?.let { StepDot(it) }
        Column(
            Modifier.weight(1f).then(if (last) Modifier else Modifier.padding(bottom = Spacing.space16)),
            content = content,
        )
    }
}

/** A step's dot: the accent and a check once done, the accent and its number for the one to do now, a ring after. */
@Composable
private fun StepDot(state: StepState, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val filled = state !is StepState.Later
    Box(
        modifier
            .size(Sizes.icon)
            .clip(CircleShape)
            .then(
                if (filled) {
                    Modifier.background(palette.accent)
                } else {
                    Modifier.border(Sizes.border, palette.textDim, CircleShape)
                },
            )
            // The order is read from the rows themselves; a done row says so as its state.
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            StepState.Done ->
                Icon(IntervalIcons.Check, null, tint = palette.onAccent, modifier = Modifier.size(Sizes.chipIcon))

            is StepState.Now ->
                Text(state.number.toString(), style = IntervalTheme.type.caption, color = palette.onAccent)

            is StepState.Later ->
                Text(state.number.toString(), style = IntervalTheme.type.caption, color = palette.textDim)
        }
    }
}
