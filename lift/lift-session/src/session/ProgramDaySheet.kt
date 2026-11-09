package dev.justinsmith.lift.session

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LinkRow
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramDay
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.estimatedMinutes
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.pick_day_all
import dev.justinsmith.lift.session.resources.pick_day_built_in
import dev.justinsmith.lift.session.resources.pick_day_next
import dev.justinsmith.lift.session.resources.pick_day_title
import dev.justinsmith.lift.session.resources.program_following
import dev.justinsmith.lift.session.resources.program_preview_day
import dev.justinsmith.lift.session.resources.programs_yours
import dev.justinsmith.lift.ui.estimateLine
import dev.justinsmith.lift.ui.programDayLifts
import dev.justinsmith.lift.ui.programName
import dev.justinsmith.lift.ui.programSummary
import org.jetbrains.compose.resources.stringResource

/**
 * A day of a program for a free session to take, from "Do a program day" under its Next set card: the programs
 * first, the one followed at the top, then the user's own, then the built-ins; a program opens on its days, each
 * with its lifts and about how long it takes, the followed program's next day marked. A tap on a day [onPick]s
 * it, the program and which of its days (from 0), and closes the sheet: the session is that day from then on.
 */
@Composable
internal fun ProgramDaySheet(state: SessionUiState, onPick: (Program, Int) -> Unit, onDismiss: () -> Unit) {
    // The program open on its days, by its stored key: a plain string saves on every platform. Null for the list.
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    val program = opened?.let { key -> state.programs.firstOrNull { it.key.stored == key } }
    IntervalSheet(
        title = program?.let { programName(it) } ?: stringResource(Res.string.pick_day_title),
        onDismiss = onDismiss,
    ) { hide ->
        Column(
            Modifier.padding(vertical = Spacing.space8),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            if (program == null) {
                ProgramChoices(state, onOpen = { opened = it.key.stored })
            } else {
                QuietTextButton(stringResource(Res.string.pick_day_all), { opened = null })
                DayChoices(program, state.followingDay, state.restSeconds) { day ->
                    onPick(program, day)
                    hide()
                }
            }
        }
    }
}

/** The programs to take a day of, in groups: the one followed, the user's own, the built-ins. */
@Composable
private fun ProgramChoices(state: SessionUiState, onOpen: (Program) -> Unit) {
    val following = state.followingDay?.program
    val (yours, builtIn) = state.programs
        .filter { it.key != following?.key }
        .partition { it.key is ProgramKey.Custom }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        following?.let { ProgramGroup(stringResource(Res.string.program_following), listOf(it), onOpen) }
        if (yours.isNotEmpty()) ProgramGroup(stringResource(Res.string.programs_yours), yours, onOpen)
        ProgramGroup(stringResource(Res.string.pick_day_built_in), builtIn, onOpen)
    }
}

@Composable
private fun ProgramGroup(title: String, programs: List<Program>, onOpen: (Program) -> Unit) {
    Column {
        GroupLabel(title)
        // On the sheet's own surface a card needs the variant fill to show as one.
        ListCard(fill = IntervalTheme.palette.surfaceVariant) {
            programs.forEachIndexed { i, program ->
                if (i > 0) RowDivider()
                LinkRow(programName(program), programSummary(program), onClick = { onOpen(program) })
            }
        }
    }
}

/**
 * Each day of [program] to take: its number, its lifts and about how long it takes at [restSeconds] between
 * sets; the one to do [next], when [program] is the one followed, is marked.
 */
@Composable
private fun DayChoices(program: Program, next: ProgramDay?, restSeconds: Int, onPick: (Int) -> Unit) {
    val palette = IntervalTheme.palette
    val nextIndex = next?.takeIf { it.program.key == program.key }?.dayIndex
    ListCard(fill = palette.surfaceVariant) {
        program.days.indices.forEach { i ->
            if (i > 0) RowDivider()
            val day = remember(program, i) { program.day(i + 1) }
            val minutes = remember(day, restSeconds) { day.estimatedMinutes(restSeconds) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.listRow)
                    .clickable(role = Role.Button) { onPick(i) }
                    .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.program_preview_day, i + 1),
                        style = IntervalTheme.type.label,
                        color = palette.text,
                    )
                    Text(programDayLifts(day), style = IntervalTheme.type.meta, color = palette.textDim)
                    Text(estimateLine(minutes), style = IntervalTheme.type.meta, color = palette.textDim)
                }
                if (i == nextIndex) {
                    Text(
                        stringResource(Res.string.pick_day_next),
                        style = IntervalTheme.type.caption,
                        color = palette.accent,
                    )
                }
                Icon(IntervalIcons.Chevron, null, tint = palette.textDim, modifier = Modifier.size(Sizes.icon))
            }
        }
    }
}
