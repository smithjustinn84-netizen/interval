package dev.justinsmith.bells.today

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
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.data.IntervalActivity
import dev.justinsmith.bells.data.IntervalWorkout
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.Program
import dev.justinsmith.bells.domain.ProgramDay
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.domain.formatKg
import dev.justinsmith.bells.domain.kettlebellLifts
import dev.justinsmith.bells.domain.suggestedBell
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.common_joined
import dev.justinsmith.bells.resources.kg_pair
import dev.justinsmith.bells.resources.kg_value
import dev.justinsmith.bells.resources.library_entry_text
import dev.justinsmith.bells.resources.library_title
import dev.justinsmith.bells.resources.program_change
import dev.justinsmith.bells.resources.program_finished
import dev.justinsmith.bells.resources.program_follow_cta
import dev.justinsmith.bells.resources.program_follow_hint
import dev.justinsmith.bells.resources.sets_count
import dev.justinsmith.bells.resources.today_bells_cd
import dev.justinsmith.bells.resources.today_free_session
import dev.justinsmith.bells.resources.today_goal_left
import dev.justinsmith.bells.resources.today_goal_met
import dev.justinsmith.bells.resources.today_last
import dev.justinsmith.bells.resources.today_last_open_cd
import dev.justinsmith.bells.resources.today_legend_bells
import dev.justinsmith.bells.resources.today_legend_interval
import dev.justinsmith.bells.resources.today_none_text
import dev.justinsmith.bells.resources.today_none_title
import dev.justinsmith.bells.resources.today_resume
import dev.justinsmith.bells.resources.today_start
import dev.justinsmith.bells.resources.today_start_day
import dev.justinsmith.bells.resources.today_this_week
import dev.justinsmith.bells.resources.today_title
import dev.justinsmith.bells.resources.today_week_count
import dev.justinsmith.bells.resources.weekday_cd
import dev.justinsmith.bells.resources.weekday_counts
import dev.justinsmith.bells.resources.weekday_initial_1
import dev.justinsmith.bells.resources.weekday_initial_2
import dev.justinsmith.bells.resources.weekday_initial_3
import dev.justinsmith.bells.resources.weekday_initial_4
import dev.justinsmith.bells.resources.weekday_initial_5
import dev.justinsmith.bells.resources.weekday_initial_6
import dev.justinsmith.bells.resources.weekday_initial_7
import dev.justinsmith.bells.resources.weekday_rest
import dev.justinsmith.bells.session.SetDraft
import dev.justinsmith.bells.ui.blockLine
import dev.justinsmith.bells.ui.liftName
import dev.justinsmith.bells.ui.programDayLine
import dev.justinsmith.bells.ui.programName
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.timer.DAYS_PER_WEEK
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.weekStartOf
import dev.justinsmith.interval.timer.weekdayOf
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.EmptyState
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LinkRow
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TabGrid
import dev.justinsmith.interval.ui.pressScale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/** One day of this week: how many sessions each app logged on it. */
data class WeekDay(
    val epochDay: Long,
    val weekday: Int,
    val bells: Int,
    val interval: Int,
    val today: Boolean,
    val date: String,
)

data class TodayUiState(
    val week: List<WeekDay> = emptyList(),
    val last: LiftSession? = null,
    /** When [last] finished: "7:42 AM" today, else its date. */
    val lastWhen: String = "",
    val inProgress: Boolean = false,
    /** The program followed, if any, and its next day: null once it is over. */
    val following: Program? = null,
    val programDay: ProgramDay? = null,
    /** The bell to suggest for each lift, with one bell and with two: the last one used for it so. */
    val bells: Map<Pair<Exercise, Boolean>, Double> = emptyMap(),
    /** Workouts a week the user aims for, lifts and intervals alike, as set on Plan; null for none. */
    val weeklyGoal: Int? = null,
) {
    val weekCount: Int get() = week.sumOf { it.bells + it.interval }
    val intervalCount: Int get() = week.sumOf { it.interval }
}

/** This week's lifts and intervals, the last session, and whether one is in progress. */
class TodayViewModel(
    private val repository: BellsRepository,
    private val intervals: IntervalActivity,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
) : ViewModel() {
    val state: StateFlow<TodayUiState> =
        combine(
            repository.sessions,
            intervals.workouts,
            repository.active,
            repository.following,
            repository.programDay,
        ) {
                sessions,
                interval,
                active,
                following,
                day,
            ->
            build(sessions, interval, active).copy(following = following, programDay = day)
        }.combine(intervals.weeklyGoal) { state, goal ->
            state.copy(weeklyGoal = goal)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            build(repository.sessions.value, intervals.workouts.value, repository.active.value)
                .copy(
                    following = repository.following.value,
                    programDay = repository.programDay.value,
                    weeklyGoal = intervals.weeklyGoal.value,
                ),
        )

    /** Starts the session about to open: [program]'s next day, or a free one with null. */
    fun start(program: ProgramKey?) {
        repository.startSession(program)
    }

    private fun build(
        sessions: List<LiftSession>,
        interval: List<IntervalWorkout>,
        active: LiftSession?,
    ): TodayUiState {
        val today = timestamps.dayNumber(clock.nowMillis())
        val bellsDays = sessions.mapNotNull { it.finishedAtEpochMillis?.let(timestamps::dayNumber) }
        val intervalDays = interval.map { timestamps.dayNumber(it.finishedAtEpochMillis) }
        val start = weekStartOf(today)
        val last = sessions.firstOrNull()
        return TodayUiState(
            week = (start until start + DAYS_PER_WEEK).map { day ->
                WeekDay(
                    epochDay = day,
                    weekday = weekdayOf(day),
                    bells = bellsDays.count { it == day },
                    interval = intervalDays.count { it == day },
                    today = day == today,
                    date = timestamps.dateOfDay(day),
                )
            },
            last = last,
            lastWhen = last?.finishedAtEpochMillis?.let { at ->
                if (timestamps.dayNumber(at) == today) timestamps.time(at) else timestamps.date(at)
            }.orEmpty(),
            inProgress = active != null,
            bells = sessions.reversed().flatMap { it.sets }.let { history ->
                kettlebellLifts.flatMap {
                    listOf(it to false, it to true)
                }.associateWith { (exercise, double) ->
                    suggestedBell(exercise, double, history, SetDraft.DEFAULT_BELL_KG)
                }
            },
        )
    }
}

@Composable
fun TodayRoute(
    onStartSession: () -> Unit,
    onChooseProgram: () -> Unit,
    onOpenBells: () -> Unit,
    onOpenSession: (uid: String) -> Unit = {},
    onOpenExercises: () -> Unit = {},
) {
    val scope = currentKoinScope()
    val viewModel = viewModel { TodayViewModel(scope.get(), scope.get(), scope.get(), scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    TodayScreen(
        state,
        onStartSession = { program ->
            if (!state.inProgress) viewModel.start(program)
            onStartSession()
        },
        onChooseProgram = onChooseProgram,
        onOpenBells = onOpenBells,
        onOpenSession = onOpenSession,
        onOpenExercises = onOpenExercises,
    )
}

/**
 * The Lift tab: the week, lifts and intervals alike, the last session (a tap opens it), and the program's
 * day. Start (or Resume) session stays pinned under them, however long the day, as Start workout does.
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
) {
    val palette = IntervalTheme.palette
    val card = Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space4)
    TabGrid(
        stringResource(Res.string.today_title),
        modifier,
        actions = { IconAction(IntervalIcons.Bell, stringResource(Res.string.today_bells_cd), onOpenBells) },
        bottomBar = { StartRow(state, onStartSession, Modifier.widthIn(max = Sizes.content)) },
    ) {
        item(key = "week", span = StaggeredGridItemSpan.FullLine) { WeekCard(state, card) }
        item(key = "last", span = StaggeredGridItemSpan.FullLine) { LastCard(state, onOpenSession, card) }
        item(key = "program", span = StaggeredGridItemSpan.FullLine) {
            ProgramCard(state, onChooseProgram, card)
        }
        item(key = "exercises", span = StaggeredGridItemSpan.FullLine) {
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
    Column(
        Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        Box(
            Modifier
                .size(Sizes.weekBarMark)
                .clip(Corners.full)
                .background(fill ?: palette.surfaceVariant)
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

/** The program day to do, with each block's lift, shape and suggested bell; or the way to pick a program. */
@Composable
private fun ProgramCard(state: TodayUiState, onChooseProgram: () -> Unit, modifier: Modifier = Modifier) {
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
        Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            Text(
                if (day == null) {
                    stringResource(Res.string.program_finished)
                } else {
                    programDayLine(day)
                },
                style = IntervalTheme.type.caption,
                color = palette.textDim,
            )
            Text(programName(following), style = IntervalTheme.type.section, color = palette.text)
        }
        day?.blocks?.forEach { block ->
            RowDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.space16, vertical = Spacing.space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(liftName(block.exercise, block.double), style = IntervalTheme.type.label, color = palette.text)
                    Text(blockLine(block), style = IntervalTheme.type.meta, color = palette.textDim)
                }
                Text(
                    stringResource(
                        if (block.double) Res.string.kg_pair else Res.string.kg_value,
                        formatKg(state.bells[block.exercise to block.double] ?: SetDraft.DEFAULT_BELL_KG),
                    ),
                    style = IntervalTheme.type.value,
                    color = palette.text,
                )
            }
        }
        RowDivider()
        LinkRow(stringResource(Res.string.program_change), null, onClick = onChooseProgram)
    }
}

/** This week, lifts and intervals alike: a mark a day, and the count. */
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
                    pluralStringResource(
                        Res.plurals.today_week_count,
                        state.weekCount,
                        state.weekCount,
                        state.intervalCount,
                    ),
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
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space16)) {
                Legend(palette.accent, stringResource(Res.string.today_legend_bells))
                Legend(palette.workMark, stringResource(Res.string.today_legend_interval))
            }
        }
    }
}

/** Resume a session in progress, or start the program's day or a free one. */
@Composable
private fun StartRow(state: TodayUiState, onStartSession: (ProgramKey?) -> Unit, modifier: Modifier = Modifier) {
    val day = state.programDay
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
}
