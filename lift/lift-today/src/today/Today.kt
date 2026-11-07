package dev.justinsmith.lift.today

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.EmptyState
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LinkRow
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.ProgressBar
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.SettingsAction
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TabGrid
import dev.justinsmith.interval.ui.pressScale
import dev.justinsmith.lift.domain.Block
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramDay
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.builtInPrograms
import dev.justinsmith.lift.domain.dayMinutes
import dev.justinsmith.lift.domain.formatKg
import dev.justinsmith.lift.today.resources.Res
import dev.justinsmith.lift.today.resources.bells_title
import dev.justinsmith.lift.today.resources.common_cancel
import dev.justinsmith.lift.today.resources.common_joined
import dev.justinsmith.lift.today.resources.kg_pair
import dev.justinsmith.lift.today.resources.kg_value
import dev.justinsmith.lift.today.resources.library_entry_text
import dev.justinsmith.lift.today.resources.library_title
import dev.justinsmith.lift.today.resources.program_finished
import dev.justinsmith.lift.today.resources.program_follow_cta
import dev.justinsmith.lift.today.resources.program_follow_hint
import dev.justinsmith.lift.today.resources.program_open_cd
import dev.justinsmith.lift.today.resources.session_leave_title
import dev.justinsmith.lift.today.resources.sets_count
import dev.justinsmith.lift.today.resources.today_all_programs
import dev.justinsmith.lift.today.resources.today_all_programs_hint
import dev.justinsmith.lift.today.resources.today_days_a_week
import dev.justinsmith.lift.today.resources.today_equipment_hint
import dev.justinsmith.lift.today.resources.today_equipment_owned
import dev.justinsmith.lift.today.resources.today_free_session
import dev.justinsmith.lift.today.resources.today_free_session_hint
import dev.justinsmith.lift.today.resources.today_group_beside
import dev.justinsmith.lift.today.resources.today_group_following
import dev.justinsmith.lift.today.resources.today_group_lately
import dev.justinsmith.lift.today.resources.today_group_own
import dev.justinsmith.lift.today.resources.today_last
import dev.justinsmith.lift.today.resources.today_last_open_cd
import dev.justinsmith.lift.today.resources.today_new_program_cd
import dev.justinsmith.lift.today.resources.today_none_text
import dev.justinsmith.lift.today.resources.today_none_title
import dev.justinsmith.lift.today.resources.today_progress_cd
import dev.justinsmith.lift.today.resources.today_resume
import dev.justinsmith.lift.today.resources.today_start
import dev.justinsmith.lift.today.resources.today_start_day
import dev.justinsmith.lift.today.resources.today_start_day_over_text
import dev.justinsmith.lift.today.resources.today_title
import dev.justinsmith.lift.today.resources.today_weeks
import dev.justinsmith.lift.ui.blockLine
import dev.justinsmith.lift.ui.estimateLine
import dev.justinsmith.lift.ui.liftName
import dev.justinsmith.lift.ui.programDayLine
import dev.justinsmith.lift.ui.programName
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TodayRoute(
    onStartSession: () -> Unit,
    onChooseProgram: () -> Unit,
    onOpenBells: () -> Unit,
    onOpenSession: (uid: String) -> Unit = {},
    onOpenExercises: () -> Unit = {},
    onOpenProgram: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onNewProgram: () -> Unit = {},
    onOpenProgramSheet: (ProgramKey) -> Unit = {},
    header: (@Composable () -> Unit)? = null,
) {
    val viewModel = koinViewModel<TodayViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    TodayScreen(
        state,
        onStartSession = { program ->
            // The page reads the session and starts none, so one is always asked for here: the store starts
            // nothing with one in progress, which is how Resume goes on, and so it is right even where this
            // state hasn't caught up with a session just finished or dropped.
            when {
                // Start day N while a session is open: the day takes the open session's place.
                state.inProgress && program != null -> viewModel.startOver(program)

                else -> viewModel.start(program)
            }
            onStartSession()
        },
        onChooseProgram = onChooseProgram,
        onOpenBells = onOpenBells,
        onOpenSession = onOpenSession,
        onOpenExercises = onOpenExercises,
        onOpenProgram = onOpenProgram,
        onOpenSettings = onOpenSettings,
        onNewProgram = onNewProgram,
        onOpenProgramSheet = onOpenProgramSheet,
        header = header,
    )
}

/**
 * Train's Strength pillar, Lift's side, in groups: the program followed (where it stands, about how long its day
 * takes, how far through it is), programs beside the mat and the wall with the way to all of them, then what there
 * is on one's own (a free session, the exercises, the equipment) and, lately, the last session and the balance.
 * Start (or Resume) stays pinned under them, as Start workout does. The week is Today's to show. [header] sits under
 * the title: the app's picker of Train's pillars.
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
    onOpenSettings: () -> Unit = {},
    onNewProgram: () -> Unit = {},
    onOpenProgramSheet: (ProgramKey) -> Unit = {},
    header: (@Composable () -> Unit)? = null,
) {
    val group = Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space4)
    TabGrid(
        stringResource(Res.string.today_title),
        modifier,
        actions = {
            IconAction(IntervalIcons.Add, stringResource(Res.string.today_new_program_cd), onNewProgram)
            SettingsAction(onOpenSettings)
        },
        bottomBar = { StartRow(state, onStartSession, Modifier.widthIn(max = Sizes.content)) },
        header = header,
    ) {
        item(key = "following") {
            Group(stringResource(Res.string.today_group_following), group) {
                ProgramCard(state, onChooseProgram, onOpenProgram)
            }
        }
        item(key = "beside") {
            Group(stringResource(Res.string.today_group_beside), group) {
                SuggestedCard(state, onOpenProgramSheet, onChooseProgram)
            }
        }
        item(key = "own") {
            Group(stringResource(Res.string.today_group_own), group) {
                OwnCard(state, onStartSession, onOpenExercises, onOpenBells)
            }
        }
        item(key = "lately") {
            Group(stringResource(Res.string.today_group_lately), group) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                    LastCard(state, onOpenSession)
                    state.balance?.let { BalanceCard(it) }
                    state.grip?.let { GripCard(it) }
                }
            }
        }
    }
}

/** A group's label over its card. */
@Composable
private fun Group(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier) {
        GroupLabel(label)
        content()
    }
}

/** The grapplers' programs not followed, each opening its sheet, then the way to every program. */
@Composable
private fun SuggestedCard(
    state: TodayUiState,
    onOpenProgramSheet: (ProgramKey) -> Unit,
    onChooseProgram: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListCard(modifier) {
        state.suggested.forEach { program ->
            LinkRow(programName(program), programLine(program, state.restSeconds), { onOpenProgramSheet(program.key) })
            RowDivider()
        }
        LinkRow(
            stringResource(Res.string.today_all_programs),
            stringResource(Res.string.today_all_programs_hint),
            onChooseProgram,
        )
    }
}

/** "2 days a week · 6 weeks · about 20 min". */
@Composable
private fun programLine(program: Program, restSeconds: Int): String = listOf(
    pluralStringResource(Res.plurals.today_days_a_week, program.daysPerWeek, program.daysPerWeek),
    pluralStringResource(Res.plurals.today_weeks, program.weeks, program.weeks),
    estimateLine(program.dayMinutes(restSeconds)),
).reduce { joined, part -> stringResource(Res.string.common_joined, joined, part) }

/** A session of one's own, while none is under way; the exercises; and the equipment, with the bells owned. */
@Composable
private fun OwnCard(
    state: TodayUiState,
    onStartSession: (ProgramKey?) -> Unit,
    onOpenExercises: () -> Unit,
    onOpenBells: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListCard(modifier) {
        if (!state.inProgress) {
            LinkRow(
                stringResource(Res.string.today_free_session),
                stringResource(Res.string.today_free_session_hint),
                { onStartSession(null) },
            )
            RowDivider()
        }
        LinkRow(
            stringResource(Res.string.library_title),
            stringResource(Res.string.library_entry_text),
            onOpenExercises,
        )
        RowDivider()
        val bells = state.ownedBells.map { stringResource(Res.string.kg_value, formatKg(it)) }
        LinkRow(
            stringResource(Res.string.bells_title),
            if (bells.isEmpty()) {
                stringResource(Res.string.today_equipment_hint)
            } else {
                stringResource(
                    Res.string.today_equipment_owned,
                    bells.reduce { joined, part ->
                        stringResource(Res.string.common_joined, joined, part)
                    },
                )
            },
            onOpenBells,
        )
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

/**
 * The program followed: where it stands and about how long its day takes, opened by a tap on its days done and
 * left, a bar of how far through it is, and the day's blocks with the load suggested for each; or the way to pick a
 * program.
 */
@Composable
private fun ProgramCard(
    state: TodayUiState,
    onChooseProgram: () -> Unit,
    onOpenProgram: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val following = state.following
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
        ProgramHeader(following, state.programDay, state.dayMinutes, onOpenProgram)
        val done = state.programDay?.let { it.number - 1 } ?: following.totalDays
        val described = stringResource(Res.string.today_progress_cd, done, following.totalDays)
        ProgressBar(
            state.progress,
            IntervalTheme.palette.accent,
            Modifier
                .padding(start = Spacing.space16, end = Spacing.space16, bottom = Spacing.space16)
                .semantics { contentDescription = described },
        )
        state.programDay?.blocks?.forEach { block ->
            RowDivider()
            BlockRow(block, state)
        }
    }
}

/** A block of the day: its lift and shape, and beside them the bell or bar suggested for it. */
@Composable
private fun BlockRow(block: Block, state: TodayUiState, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Row(
        modifier
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

/**
 * Where the program stands over its name, opened by a tap: its days done and left. Beside them, about how long
 * the day takes ([minutes]).
 */
@Composable
private fun ProgramHeader(following: Program, day: ProgramDay?, minutes: Int?, onOpenProgram: () -> Unit) {
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
        if (minutes != null) Text(estimateLine(minutes), style = IntervalTheme.type.meta, color = palette.textDim)
        Icon(
            IntervalIcons.Chevron,
            contentDescription = null,
            tint = palette.textDim,
            modifier = Modifier.size(Sizes.icon),
        )
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

            // A free session is a row of its own, under On your own.
            day != null -> AccentButton(
                stringResource(Res.string.today_start_day, day.number),
                onClick = { onStartSession(day.program.key) },
                modifier = Modifier.fillMaxWidth(),
            )

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
