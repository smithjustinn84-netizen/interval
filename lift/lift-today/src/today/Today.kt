package dev.justinsmith.lift.today

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.EmptyState
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LinkRow
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StandardEasing
import dev.justinsmith.interval.ui.TabGrid
import dev.justinsmith.interval.ui.pressScale
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramDay
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.builtInPrograms
import dev.justinsmith.lift.domain.formatKg
import dev.justinsmith.lift.today.resources.Res
import dev.justinsmith.lift.today.resources.common_cancel
import dev.justinsmith.lift.today.resources.common_joined
import dev.justinsmith.lift.today.resources.kg_pair
import dev.justinsmith.lift.today.resources.kg_value
import dev.justinsmith.lift.today.resources.library_entry_text
import dev.justinsmith.lift.today.resources.library_title
import dev.justinsmith.lift.today.resources.program_change
import dev.justinsmith.lift.today.resources.program_finished
import dev.justinsmith.lift.today.resources.program_follow_cta
import dev.justinsmith.lift.today.resources.program_follow_hint
import dev.justinsmith.lift.today.resources.program_open_cd
import dev.justinsmith.lift.today.resources.session_leave_title
import dev.justinsmith.lift.today.resources.sets_count
import dev.justinsmith.lift.today.resources.today_bells_cd
import dev.justinsmith.lift.today.resources.today_free_session
import dev.justinsmith.lift.today.resources.today_goal_left
import dev.justinsmith.lift.today.resources.today_goal_met
import dev.justinsmith.lift.today.resources.today_last
import dev.justinsmith.lift.today.resources.today_last_open_cd
import dev.justinsmith.lift.today.resources.today_legend_bells
import dev.justinsmith.lift.today.resources.today_legend_interval
import dev.justinsmith.lift.today.resources.today_none_text
import dev.justinsmith.lift.today.resources.today_none_title
import dev.justinsmith.lift.today.resources.today_resume
import dev.justinsmith.lift.today.resources.today_start
import dev.justinsmith.lift.today.resources.today_start_day
import dev.justinsmith.lift.today.resources.today_start_day_over_text
import dev.justinsmith.lift.today.resources.today_this_week
import dev.justinsmith.lift.today.resources.today_title
import dev.justinsmith.lift.today.resources.today_week_count
import dev.justinsmith.lift.today.resources.today_week_workouts
import dev.justinsmith.lift.today.resources.weekday_cd
import dev.justinsmith.lift.today.resources.weekday_counts
import dev.justinsmith.lift.today.resources.weekday_initial_1
import dev.justinsmith.lift.today.resources.weekday_initial_2
import dev.justinsmith.lift.today.resources.weekday_initial_3
import dev.justinsmith.lift.today.resources.weekday_initial_4
import dev.justinsmith.lift.today.resources.weekday_initial_5
import dev.justinsmith.lift.today.resources.weekday_initial_6
import dev.justinsmith.lift.today.resources.weekday_initial_7
import dev.justinsmith.lift.today.resources.weekday_rest
import dev.justinsmith.lift.ui.blockLine
import dev.justinsmith.lift.ui.liftName
import dev.justinsmith.lift.ui.programDayLine
import dev.justinsmith.lift.ui.programName
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

@Composable
fun TodayRoute(
    onStartSession: () -> Unit,
    onChooseProgram: () -> Unit,
    onOpenBells: () -> Unit,
    onOpenSession: (uid: String) -> Unit = {},
    onOpenExercises: () -> Unit = {},
    onOpenProgram: () -> Unit = {},
) {
    val scope = currentKoinScope()
    val viewModel = viewModel { TodayViewModel(scope.get(), scope.get(), scope.get(), scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    TodayScreen(
        state,
        onStartSession = { program ->
            when {
                !state.inProgress -> viewModel.start(program)

                // Start day N while a session is open: the day takes the open session's place.
                program != null -> viewModel.startOver(program)
            }
            onStartSession()
        },
        onChooseProgram = onChooseProgram,
        onOpenBells = onOpenBells,
        onOpenSession = onOpenSession,
        onOpenExercises = onOpenExercises,
        onOpenProgram = onOpenProgram,
    )
}

/**
 * The Lift tab: the week, lifts and intervals alike, the program's day (what's next before what's past), the
 * last session (a tap opens it) and the exercises. Start (or Resume) session stays pinned under them, however
 * long the day, as Start workout does. On a wider window the week is a column's card like the others, so its
 * days sit close and what's next sits beside it.
 */
@Composable
fun TodayScreen(
    state: TodayUiState,
    onStartSession: (ProgramKey?) -> Unit,
    onChooseProgram: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenBells: () -> Unit = {},
    onOpenSession: (uid: String) -> Unit = {},
    onOpenExercises: () -> Unit = {},
    onOpenProgram: () -> Unit = {},
) {
    val palette = IntervalTheme.palette
    val card = Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space4)
    TabGrid(
        stringResource(Res.string.today_title),
        modifier,
        actions = { IconAction(IntervalIcons.Bell, stringResource(Res.string.today_bells_cd), onOpenBells) },
        bottomBar = { StartRow(state, onStartSession, Modifier.widthIn(max = Sizes.content)) },
    ) {
        item(key = "week") { WeekCard(state, card) }
        item(key = "program") { ProgramCard(state, onChooseProgram, onOpenProgram, card) }
        item(key = "last") { LastCard(state, onOpenSession, card) }
        item(key = "exercises") {
            ListCard(card) {
                LinkRow(
                    stringResource(Res.string.library_title),
                    stringResource(Res.string.library_entry_text),
                    onOpenExercises,
                )
            }
        }
    }
}

/** The last session, opened by a tap; or, before the first, what a session is. */
@Composable
private fun LastCard(state: TodayUiState, onOpenSession: (uid: String) -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    ListCard(modifier) {
        val last = state.last
        if (last == null) {
            EmptyState(
                IntervalIcons.Bell,
                stringResource(Res.string.today_none_title),
                stringResource(Res.string.today_none_text),
            )
        } else {
            val interaction = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interaction,
                        LocalIndication.current,
                        onClickLabel = stringResource(Res.string.today_last_open_cd),
                        role = Role.Button,
                    ) { onOpenSession(last.uid) }
                    // After the ripple, as a link row does: the content shrinks, the ripple keeps the card's width.
                    .pressScale(interaction)
                    .padding(
                        start = Spacing.space16,
                        top = Spacing.space16,
                        bottom = Spacing.space16,
                        end = Spacing.space12,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space4),
                ) {
                    Text(
                        stringResource(Res.string.today_last),
                        style = IntervalTheme.type.caption,
                        color = palette.textDim,
                    )
                    Text(last.title, style = IntervalTheme.type.stat, color = palette.text)
                    val detail = listOf(
                        pluralStringResource(Res.plurals.sets_count, last.sets.size, last.sets.size),
                        stringResource(Res.string.kg_value, formatKg(last.tonnageKg)),
                        formatClock(last.elapsedSeconds(0)),
                        state.lastWhen,
                    ).reduce { joined, part -> stringResource(Res.string.common_joined, joined, part) }
                    Text(detail, style = IntervalTheme.type.meta, color = palette.textDim)
                }
                Icon(
                    IntervalIcons.Chevron,
                    contentDescription = null,
                    tint = palette.textDim,
                    modifier = Modifier.size(Sizes.icon),
                )
            }
        }
    }
}

/** A day of the week strip: filled in the accent for a kettlebell session, in the work mark for intervals only. */
@Composable
private fun DayMark(day: WeekDay) {
    val palette = IntervalTheme.palette
    val fill: Color? = when {
        day.bells > 0 -> palette.accent
        day.interval > 0 -> palette.workMark
        else -> null
    }
    val counts = if (day.bells + day.interval == 0) {
        stringResource(Res.string.weekday_rest)
    } else {
        stringResource(Res.string.weekday_counts, day.bells, day.interval)
    }
    val description = stringResource(Res.string.weekday_cd, day.date, counts)
    // A day's dot takes its colour as the first workout lands, rather than switching.
    val dot by animateColorAsState(
        fill ?: palette.surfaceVariant,
        tween(Motion.STATE_MILLIS, easing = StandardEasing),
        label = "day",
    )
    Column(
        Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        Box(
            Modifier
                .size(Sizes.weekBarMark)
                .clip(Corners.full)
                .background(dot)
                .then(if (day.today) Modifier.border(Sizes.border, palette.text, Corners.full) else Modifier),
        )
        Text(
            stringResource(weekdayInitials[day.weekday - 1]),
            style = IntervalTheme.type.caption,
            color = if (day.today) palette.text else palette.textDim,
        )
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.space4)) {
        Box(Modifier.size(Sizes.legendSwatch).clip(Corners.full).background(color))
        Text(text, style = IntervalTheme.type.caption, color = IntervalTheme.palette.textDim)
    }
}

private val weekdayInitials = listOf(
    Res.string.weekday_initial_1,
    Res.string.weekday_initial_2,
    Res.string.weekday_initial_3,
    Res.string.weekday_initial_4,
    Res.string.weekday_initial_5,
    Res.string.weekday_initial_6,
    Res.string.weekday_initial_7,
)

/**
 * The program day to do, with each block's lift, shape and suggested bell; or the way to pick a program. A tap
 * on the header opens the program: its days done and left, or once it is finished what it came to.
 */
@Composable
private fun ProgramCard(
    state: TodayUiState,
    onChooseProgram: () -> Unit,
    onOpenProgram: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    val following = state.following
    val day = state.programDay
    if (following == null) {
        ListCard(modifier) {
            LinkRow(
                stringResource(Res.string.program_follow_cta),
                stringResource(Res.string.program_follow_hint),
                onClick = onChooseProgram,
            )
        }
        return
    }
    ListCard(modifier) {
        ProgramHeader(following, day, onOpenProgram)
        day?.blocks?.forEach { block ->
            RowDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(liftName(block.exercise, block.double), style = IntervalTheme.type.label, color = palette.text)
                    // A bell's or a bar's load shows beside it, as planned or as last used.
                    Text(
                        blockLine(block, withLoad = !block.exercise.hasSuggestedLoad),
                        style = IntervalTheme.type.meta,
                        color = palette.textDim,
                    )
                }
                // Only a bell or a bar has a weight to suggest ahead of the session.
                if (block.exercise.hasSuggestedLoad) {
                    Text(
                        stringResource(
                            if (block.double) Res.string.kg_pair else Res.string.kg_value,
                            formatKg(state.suggestedKg(block)),
                        ),
                        style = IntervalTheme.type.value,
                        color = palette.text,
                    )
                }
            }
        }
        RowDivider()
        LinkRow(stringResource(Res.string.program_change), null, onClick = onChooseProgram)
    }
}

/** Where the program stands over its name, opened by a tap: its days done and left. */
@Composable
private fun ProgramHeader(following: Program, day: ProgramDay?, onOpenProgram: () -> Unit) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interaction,
                LocalIndication.current,
                onClickLabel = stringResource(Res.string.program_open_cd),
                role = Role.Button,
            ) { onOpenProgram() }
            .pressScale(interaction)
            .padding(
                start = Spacing.space16,
                top = Spacing.space16,
                bottom = Spacing.space16,
                end = Spacing.space12,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            Text(
                if (day == null) stringResource(Res.string.program_finished) else programDayLine(day),
                style = IntervalTheme.type.caption,
                color = palette.textDim,
            )
            Text(
                programName(following),
                style = IntervalTheme.type.section,
                color = palette.text,
                modifier = Modifier.semantics { heading() },
            )
        }
        Icon(
            IntervalIcons.Chevron,
            contentDescription = null,
            tint = palette.textDim,
            modifier = Modifier.size(Sizes.icon),
        )
    }
}

/**
 * This week, lifts and intervals alike: a mark a day, and the count. The Timer's share of the count, and the
 * legend saying which mark is which, wait until there is something for them to tell apart.
 */
@Composable
private fun WeekCard(state: TodayUiState, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    ListCard(modifier) {
        Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.today_this_week),
                    style = IntervalTheme.type.caption,
                    color = palette.textDim,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (state.intervalCount > 0) {
                        pluralStringResource(
                            Res.plurals.today_week_count,
                            state.weekCount,
                            state.weekCount,
                            state.intervalCount,
                        )
                    } else {
                        pluralStringResource(Res.plurals.today_week_workouts, state.weekCount, state.weekCount)
                    },
                    style = IntervalTheme.type.meta,
                    color = palette.textDim,
                )
            }
            state.weeklyGoal?.let { goal ->
                val left = goal - state.weekCount
                Text(
                    if (left <= 0) {
                        stringResource(Res.string.today_goal_met)
                    } else {
                        pluralStringResource(Res.plurals.today_goal_left, left, left, goal)
                    },
                    style = IntervalTheme.type.title,
                    color = palette.text,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                state.week.forEach { day -> DayMark(day) }
            }
            if (state.weekCount > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space16)) {
                    Legend(palette.accent, stringResource(Res.string.today_legend_bells))
                    Legend(palette.workMark, stringResource(Res.string.today_legend_interval))
                }
            }
        }
    }
}

/**
 * Resume a session in progress, or start the program's day or a free one. While a session is open and a day is
 * pending, both: Resume first, and Start day N, which takes the open session's place, asking first if it has
 * sets.
 */
@Composable
private fun StartRow(state: TodayUiState, onStartSession: (ProgramKey?) -> Unit, modifier: Modifier = Modifier) {
    val day = state.programDay
    var asking by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier.padding(
            start = Spacing.space16,
            end = Spacing.space16,
            top = Spacing.space8,
            bottom = Spacing.space16,
        ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        when {
            state.inProgress && day != null -> {
                SecondaryButton(
                    stringResource(Res.string.today_start_day, day.number),
                    { if (state.openHasSets) asking = true else onStartSession(day.program.key) },
                    Modifier.weight(1f),
                )
                AccentButton(
                    stringResource(Res.string.today_resume),
                    onClick = { onStartSession(null) },
                    modifier = Modifier.weight(1f),
                )
            }

            state.inProgress -> AccentButton(
                stringResource(Res.string.today_resume),
                onClick = { onStartSession(null) },
                modifier = Modifier.fillMaxWidth(),
            )

            day != null -> {
                SecondaryButton(stringResource(Res.string.today_free_session), {
                    onStartSession(null)
                }, Modifier.weight(1f))
                AccentButton(
                    stringResource(Res.string.today_start_day, day.number),
                    onClick = { onStartSession(day.program.key) },
                    modifier = Modifier.weight(1f),
                )
            }

            else -> AccentButton(
                stringResource(Res.string.today_start),
                onClick = { onStartSession(null) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (asking && day != null) {
        ConfirmDialog(
            title = stringResource(Res.string.session_leave_title),
            text = stringResource(Res.string.today_start_day_over_text),
            confirm = stringResource(Res.string.today_start_day, day.number),
            dismiss = stringResource(Res.string.common_cancel),
            onConfirm = {
                asking = false
                onStartSession(day.program.key)
            },
            onDismiss = { asking = false },
        )
    }
}

@PreviewFormFactors
@Composable
private fun TodayScreenPreview() {
    IntervalTheme(darkTheme = true) {
        TodayScreen(
            TodayUiState(
                last = LiftSession(
                    "s",
                    startedAtEpochMillis = 0,
                    finishedAtEpochMillis = 2_400_000,
                    title = "Day 1",
                    sets = emptyList(),
                ),
                lastWhen = "7:42 AM",
                following = builtInPrograms.first(),
            ),
            onStartSession = {},
            onChooseProgram = {},
        )
    }
}

/** Whether Today suggests a weight for [this] ahead of the session: a bell lift's, or a barbell lift's. */
private val Exercise.hasSuggestedLoad: Boolean
    get() = equipment == Equipment.Kettlebell || equipment == Equipment.Barbell
