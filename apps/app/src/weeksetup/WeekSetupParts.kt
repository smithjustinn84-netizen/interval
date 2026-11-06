@file:OptIn(ExperimentalLayoutApi::class)

package dev.justinsmith.interval.weeksetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.plan.sportName
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.setup_bell_cd
import dev.justinsmith.interval.resources.setup_bells
import dev.justinsmith.interval.resources.setup_days
import dev.justinsmith.interval.resources.setup_goal
import dev.justinsmith.interval.resources.setup_minutes
import dev.justinsmith.interval.resources.setup_minutes_value
import dev.justinsmith.interval.resources.setup_pick_program
import dev.justinsmith.interval.resources.setup_sport
import dev.justinsmith.interval.resources.setup_sport_another
import dev.justinsmith.interval.resources.setup_sport_name_label
import dev.justinsmith.interval.resources.setup_sport_name_placeholder
import dev.justinsmith.interval.resources.setup_sport_none
import dev.justinsmith.interval.resources.setup_week
import dev.justinsmith.interval.resources.setup_week_days
import dev.justinsmith.interval.resources.setup_week_empty
import dev.justinsmith.interval.resources.setup_week_kept
import dev.justinsmith.interval.resources.setup_week_name_sport
import dev.justinsmith.interval.resources.setup_week_no_bell
import dev.justinsmith.interval.resources.setup_week_no_day
import dev.justinsmith.interval.resources.setup_week_no_room
import dev.justinsmith.interval.resources.setup_week_pick_days
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.NameField
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SegmentedPicker
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.WeekdayForm
import dev.justinsmith.interval.ui.WeekdayPicker
import dev.justinsmith.interval.ui.joined
import dev.justinsmith.interval.ui.weekdayText
import dev.justinsmith.lift.domain.formatKg
import dev.justinsmith.lift.ui.Choice
import dev.justinsmith.lift.ui.ChoiceRow
import dev.justinsmith.lift.ui.estimateLine
import dev.justinsmith.lift.ui.programName
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// Set up's four questions and the week they come to.

/** What else you train: the sports named, one of your own (named under the picks), or the bell alone. */
@Composable
internal fun SportQuestion(state: WeekSetupUiState, actions: WeekSetupActions) {
    GroupLabel(stringResource(Res.string.setup_sport))
    ChoiceRow(
        options = (0..SPORT_NONE).toList(),
        selected = state.sport.takeIf { it != SPORT_UNANSWERED },
        label = { sportChoice(it) },
        onSelect = actions.onSport,
    )
    if (state.sport == SPORT_ANOTHER) {
        val name = rememberTextFieldState(state.sportName)
        LaunchedEffect(name) { snapshotFlow { name.text.toString() }.collect(actions.onSportName) }
        NameField(
            name,
            placeholder = stringResource(Res.string.setup_sport_name_placeholder),
            label = stringResource(Res.string.setup_sport_name_label),
            maxLength = Sport.NAME_MAX_LENGTH,
            modifier = Modifier.padding(top = Spacing.space12),
            capitalization = KeyboardCapitalization.Words,
        )
    }
}

@Composable
private fun sportChoice(index: Int): String = when (index) {
    SPORT_ANOTHER -> stringResource(Res.string.setup_sport_another)
    SPORT_NONE -> stringResource(Res.string.setup_sport_none)
    else -> sportName(Sport.named[index])
}

/** On which days the sport is, none picked to begin with. */
@Composable
internal fun DaysQuestion(weekdays: Int, onChange: (Int) -> Unit) {
    Column {
        GroupLabel(stringResource(Res.string.setup_days))
        WeekdayPicker(weekdays, onChange, allowNone = true)
    }
}

/** The bells owned, each a size in kg to tick. */
@Composable
internal fun BellsQuestion(state: WeekSetupUiState, onToggle: (Double) -> Unit) {
    GroupLabel(stringResource(Res.string.setup_bells))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        state.bellChoices.forEach { kg ->
            val size = formatKg(kg)
            val described = stringResource(Res.string.setup_bell_cd, size)
            Choice(
                size,
                kg in state.bells,
                onClick = { onToggle(kg) },
                modifier = Modifier.semantics { contentDescription = described },
                role = Role.Checkbox,
            )
        }
    }
}

/** How long a session is. */
@Composable
internal fun MinutesQuestion(minutes: Int, onPick: (Int) -> Unit) {
    GroupLabel(stringResource(Res.string.setup_minutes))
    SegmentedPicker(
        AppSettings.SESSION_MINUTES,
        minutes,
        label = { stringResource(Res.string.setup_minutes_value, it) },
        onSelect = onPick,
        check = false,
    )
}

/**
 * The week the answers come to, before it is built: the sport on its days, the program by name with about how
 * long a day of it takes and the days it goes on, and the goal they add up to. [onPrograms] leads to Lift's
 * programs, for anyone who would follow another.
 */
@Composable
internal fun YourWeek(state: WeekSetupUiState, onPrograms: () -> Unit) {
    val week = state.week
    GroupLabel(stringResource(Res.string.setup_week))
    ListCard {
        if (state.unanswered) {
            Text(
                stringResource(Res.string.setup_week_empty),
                style = IntervalTheme.type.meta,
                color = IntervalTheme.palette.textDim,
                modifier = Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space12),
            )
        } else {
            if (state.asksDays) {
                SportLine(state)
                RowDivider()
            }
            StrengthLine(state)
            week.goal?.let { goal ->
                RowDivider()
                WeekLine(pluralStringResource(Res.plurals.setup_goal, goal, goal))
            }
        }
    }
    if (week.program != null) {
        QuietTextButton(
            stringResource(Res.string.setup_pick_program),
            onPrograms,
            Modifier.padding(top = Spacing.space4),
        )
    }
}

/** The sport and its days, or what is still missing of them. */
@Composable
private fun SportLine(state: WeekSetupUiState) {
    val sport = state.answeredSport
    val name = sport?.let { sportName(it) } ?: stringResource(Res.string.setup_sport_another)
    val detail = when {
        sport == null -> stringResource(Res.string.setup_week_name_sport)
        state.week.sportDays.isEmpty() -> stringResource(Res.string.setup_week_pick_days)
        else -> daysLine(state.week.sportDays)
    }
    WeekLine(name, detail)
}

/** The program and its days, or why there is none. */
@Composable
private fun StrengthLine(state: WeekSetupUiState) {
    val week = state.week
    val program = week.program
    if (program == null) {
        val why = if (state.bells.isEmpty()) Res.string.setup_week_no_bell else Res.string.setup_week_no_room
        WeekLine(stringResource(why))
        return
    }
    val about = if (week.keepsProgram) {
        stringResource(Res.string.setup_week_kept)
    } else {
        state.programMinutes?.let { estimateLine(it) }
    }
    val days = if (week.liftDays.isEmpty()) stringResource(Res.string.setup_week_no_day) else daysLine(week.liftDays)
    WeekLine(programName(program), joined(listOfNotNull(about, days)))
}

/** "Mon, Wed, Fri". */
@Composable
private fun daysLine(days: Set<Int>): String = days.sorted()
    .map { weekdayText(it, WeekdayForm.Short) }
    .reduce { all, day -> stringResource(Res.string.setup_week_days, all, day) }

/** A line of the week: what it is over how it stands, read out as one. */
@Composable
private fun WeekLine(name: String, detail: String? = null) {
    val palette = IntervalTheme.palette
    Column(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(name, style = IntervalTheme.type.label, color = palette.text)
        if (detail != null) Text(detail, style = IntervalTheme.type.meta, color = palette.textDim)
    }
}
