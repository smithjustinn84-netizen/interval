package dev.justinsmith.interval.plan

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.common_joined
import dev.justinsmith.interval.history.resources.history_kind_interval
import dev.justinsmith.interval.history.resources.history_kind_lift
import dev.justinsmith.interval.history.resources.notice_plan_added
import dev.justinsmith.interval.history.resources.plan_add_confirm
import dev.justinsmith.interval.history.resources.plan_add_title
import dev.justinsmith.interval.history.resources.plan_every_week
import dev.justinsmith.interval.history.resources.plan_lift_any
import dev.justinsmith.interval.history.resources.plan_lift_any_text
import dev.justinsmith.interval.history.resources.plan_lift_days_hint
import dev.justinsmith.interval.history.resources.plan_lift_days_use
import dev.justinsmith.interval.history.resources.plan_lift_none_text
import dev.justinsmith.interval.history.resources.plan_lift_none_title
import dev.justinsmith.interval.history.resources.plan_lift_program_next
import dev.justinsmith.interval.history.resources.plan_on_timer
import dev.justinsmith.interval.history.resources.plan_once
import dev.justinsmith.interval.history.resources.plan_when
import dev.justinsmith.interval.history.resources.plan_workout
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.asPlan
import dev.justinsmith.interval.timer.DAYS_PER_WEEK
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SegmentedPicker
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.WeekdayForm
import dev.justinsmith.interval.ui.joined
import dev.justinsmith.interval.ui.pressScale
import dev.justinsmith.interval.ui.surfaceButton
import dev.justinsmith.interval.ui.weekdayText
import dev.justinsmith.interval.ui.workoutDescription
import dev.justinsmith.lift.domain.ProgramDay
import dev.justinsmith.lift.ui.programDayLine
import dev.justinsmith.lift.ui.programName
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Whether a new plan repeats every week or falls once. */
enum class PlanRepeat { Weekly, Once }

/** The two kinds of plan the sheet offers. */
private enum class PlanKind { Interval, Lift }

/** What the add sheet offers on Lift's side: the next day of the program followed, when one is. */
data class LiftChoices(val programDay: ProgramDay?)

/**
 * Plans a workout: one of [choices], or with [lift] a session on Lift, then every week on the weekdays picked or
 * once on one of [days]. It opens on [repeat] with [weekday] or [epochDay] picked, so the way in (a weekday's +,
 * a date) is already chosen. Without [lift] it plans only an interval workout, as a workout's page does.
 */
@Composable
fun AddToPlanSheet(
    choices: List<PlanChoice>,
    days: List<DayChoice>,
    onPlanWeekly: (PlanWorkout, Set<Int>) -> Unit,
    onPlanOnce: (PlanWorkout, Long) -> Unit,
    onDismiss: () -> Unit,
    repeat: PlanRepeat = PlanRepeat.Weekly,
    weekday: Int = 1,
    epochDay: Long = days.firstOrNull()?.epochDay ?: 0,
    lift: LiftChoices? = null,
) {
    val palette = IntervalTheme.palette
    // The first choice is the Timer's; a preset is the likelier pick when there is one.
    var picked by rememberSaveable { mutableIntStateOf(if (choices.size > 1) 1 else 0) }
    var kind by rememberSaveable { mutableStateOf(PlanKind.Interval) }
    val programDay = lift?.programDay
    // The program's next day when one is followed, else any session, the only one offered then.
    var ofProgram by rememberSaveable { mutableStateOf(true) }
    var mode by rememberSaveable { mutableStateOf(repeat) }
    // The weekdays picked, as bits: Monday is bit 0.
    var weekdays by rememberSaveable { mutableIntStateOf(1 shl (weekday - 1)) }
    var day by rememberSaveable { mutableLongStateOf(epochDay) }
    val title = stringResource(Res.string.plan_add_title)
    IntervalSheet(title = title, onDismiss = onDismiss) { hide ->
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
            Text(
                title,
                style = IntervalTheme.type.title,
                color = palette.text,
                modifier = Modifier.semantics { heading() },
            )
            if (lift != null) KindPicker(kind, onPick = { kind = it })
            when (kind) {
                PlanKind.Interval -> IntervalChoiceList(choices, picked, onPick = { picked = it })
                PlanKind.Lift -> LiftChoiceList(programDay, ofProgram = ofProgram, onPick = { ofProgram = it })
            }
            // The program's days a week, offered spread over the week while its next day is the pick.
            val pace = programDay?.program?.daysPerWeek
                ?.takeIf { kind == PlanKind.Lift && ofProgram && it in 2 until DAYS_PER_WEEK }
            WhenPicker(
                mode = mode,
                onMode = { mode = it },
                weekdays = weekdays,
                onWeekdays = { weekdays = it },
                days = days,
                day = day,
                onDay = { day = it },
                pace = pace?.let { it to spreadWeekdays(weekday, it) },
            )
            val notices = LocalNotices.current
            val added = stringResource(Res.string.notice_plan_added)
            val canAdd = kind == PlanKind.Lift || choices.isNotEmpty()
            AccentButton(
                stringResource(Res.string.plan_add_confirm),
                onClick = {
                    if (!canAdd) return@AccentButton
                    val workout = when (kind) {
                        PlanKind.Interval -> choices[picked.coerceIn(choices.indices)].preset.asPlan()
                        PlanKind.Lift -> liftPlan(ofProgram && programDay != null)
                    }
                    when (mode) {
                        PlanRepeat.Weekly -> onPlanWeekly(
                            workout,
                            (1..DAYS_PER_WEEK).filter { weekdays and (1 shl (it - 1)) != 0 }.toSet(),
                        )

                        PlanRepeat.Once -> onPlanOnce(workout, day)
                    }
                    notices.show(added)
                    hide()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canAdd,
            )
        }
    }
}

/** The session on Lift planned: the program's next day when [ofProgram], else any session. */
private fun liftPlan(ofProgram: Boolean): PlanWorkout.Lift =
    if (ofProgram) PlanWorkout.Lift.ProgramDay else PlanWorkout.Lift.AnySession

/** Interval or Lift. */
@Composable
private fun KindPicker(kind: PlanKind, onPick: (PlanKind) -> Unit) {
    SegmentedPicker(
        options = PlanKind.entries,
        selected = kind,
        label = {
            when (it) {
                PlanKind.Interval -> stringResource(Res.string.history_kind_interval)
                PlanKind.Lift -> stringResource(Res.string.history_kind_lift)
            }
        },
        onSelect = onPick,
    )
}

/** The interval workouts to pick from, the [picked] one checked; nothing to pick with only one. */
@Composable
private fun IntervalChoiceList(choices: List<PlanChoice>, picked: Int, onPick: (Int) -> Unit) {
    if (choices.size <= 1) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
        Caption(stringResource(Res.string.plan_workout))
        ListCard(Modifier.selectableGroup()) {
            choices.forEachIndexed { index, choice ->
                if (index > 0) RowDivider()
                ChoiceRow(choice, selected = index == picked, onPick = { onPick(index) })
            }
        }
    }
}

/**
 * Every week on the [weekdays] picked, or once on [day]; with [pace], the program's days a week and the weekdays
 * they spread over, a tap that picks those.
 */
@Suppress("detekt.LongParameterList") // The two ways a plan repeats, each with its own pick.
@Composable
private fun WhenPicker(
    mode: PlanRepeat,
    onMode: (PlanRepeat) -> Unit,
    weekdays: Int,
    onWeekdays: (Int) -> Unit,
    days: List<DayChoice>,
    day: Long,
    onDay: (Long) -> Unit,
    pace: Pair<Int, Set<Int>>?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
        Caption(stringResource(Res.string.plan_when))
        SegmentedPicker(
            options = PlanRepeat.entries,
            selected = mode,
            label = {
                when (it) {
                    PlanRepeat.Weekly -> stringResource(Res.string.plan_every_week)
                    PlanRepeat.Once -> stringResource(Res.string.plan_once)
                }
            },
            onSelect = onMode,
        )
        when (mode) {
            PlanRepeat.Weekly -> {
                WeekdayPicker(weekdays, onChange = onWeekdays)
                pace?.let { (perWeek, spread) -> ProgramPace(perWeek, spread, weekdays, onUse = onWeekdays) }
            }

            PlanRepeat.Once -> DayPicker(days, day, onPick = onDay)
        }
    }
}

/**
 * Lift's side: the next day of the program followed or any session, [ofProgram] picking the first; with no
 * program followed, a word on what following one does, over any session alone.
 */
@Composable
private fun LiftChoiceList(programDay: ProgramDay?, ofProgram: Boolean, onPick: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
        if (programDay == null) NoProgram()
        Caption(stringResource(Res.string.plan_workout))
        ListCard(Modifier.selectableGroup()) {
            if (programDay != null) {
                PickRow(
                    name = programName(programDay.program),
                    detail = stringResource(Res.string.plan_lift_program_next, programDayLine(programDay)),
                    selected = ofProgram,
                    onPick = { onPick(true) },
                )
                RowDivider()
            }
            PickRow(
                name = stringResource(Res.string.plan_lift_any),
                detail = stringResource(Res.string.plan_lift_any_text),
                selected = programDay == null || !ofProgram,
                onPick = { onPick(false) },
            )
        }
    }
}

/** What following a program on Lift does, while none is followed. */
@Composable
private fun NoProgram() {
    val palette = IntervalTheme.palette
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Corners.radius18)
            .background(palette.surfaceVariant)
            .padding(Spacing.space16),
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        Text(stringResource(Res.string.plan_lift_none_title), style = IntervalTheme.type.label, color = palette.text)
        Text(stringResource(Res.string.plan_lift_none_text), style = IntervalTheme.type.meta, color = palette.textDim)
    }
}

/** The days a week the program asks for, with a tap that spreads them over the week ([suggested]). */
@Composable
private fun ProgramPace(pace: Int, suggested: Set<Int>, weekdays: Int, onUse: (Int) -> Unit) {
    val palette = IntervalTheme.palette
    val bits = suggested.sumOf { 1 shl (it - 1) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Corners.radius12)
            .background(palette.surfaceVariant)
            .padding(start = Spacing.space12, end = Spacing.space4, top = Spacing.space4, bottom = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Text(
            pluralStringResource(Res.plurals.plan_lift_days_hint, pace, pace),
            style = IntervalTheme.type.meta,
            color = palette.textDim,
            modifier = Modifier.weight(1f),
        )
        if (bits != weekdays) {
            val names = suggested.sorted().map { weekdayText(it, WeekdayForm.Short) }
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .heightIn(min = Sizes.pillHeight)
                    .surfaceButton(interaction, Corners.radius12, palette.surface, { onUse(bits) })
                    .padding(horizontal = Spacing.space12),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(Res.string.plan_lift_days_use, joined(names)),
                    style = IntervalTheme.type.label,
                    color = palette.accent,
                )
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = IntervalTheme.type.caption, color = IntervalTheme.palette.textDim)
}

@Composable
private fun ChoiceRow(choice: PlanChoice, selected: Boolean, onPick: () -> Unit) {
    val shape = workoutDescription(choice.preset.config)
    PickRow(
        name = if (choice.onTimer) stringResource(Res.string.plan_on_timer) else choice.preset.name,
        detail = if (choice.onTimer) stringResource(Res.string.common_joined, choice.preset.name, shape) else shape,
        selected = selected,
        onPick = onPick,
    )
}

/** One choice of a group: its [name] over a line of [detail], checked when [selected]. */
@Composable
private fun PickRow(name: String, detail: String, selected: Boolean, onPick: () -> Unit) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .selectable(selected, interaction, LocalIndication.current, role = Role.RadioButton, onClick = onPick)
            .pressScale(interaction)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Box(Modifier.size(Sizes.icon), contentAlignment = Alignment.Center) {
            if (selected) Icon(IntervalIcons.Check, null, tint = palette.accent, modifier = Modifier.size(Sizes.icon))
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = IntervalTheme.type.label, color = palette.text)
            Text(detail, style = IntervalTheme.type.meta, color = palette.textDim)
        }
    }
}

/**
 * Monday to Sunday as toggles over [weekdays], a bit per day (Monday is bit 0). At least one day stays
 * picked, so Add always has somewhere to put the workout.
 */
@Composable
private fun WeekdayPicker(weekdays: Int, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space4)) {
        for (weekday in 1..DAYS_PER_WEEK) {
            val bit = 1 shl (weekday - 1)
            DayToggle(
                label = weekdayText(weekday, WeekdayForm.Initial),
                description = weekdayText(weekday),
                on = weekdays and bit != 0,
                onToggle = { onChange((weekdays xor bit).takeIf { it != 0 } ?: weekdays) },
                role = Role.Checkbox,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** [days] a week to a row, the [picked] one accent; a short last row keeps the columns. */
@Composable
private fun DayPicker(days: List<DayChoice>, picked: Long, onPick: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
        days.chunked(DAYS_PER_WEEK).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space4)) {
                week.forEach { choice ->
                    val initial = weekdayText(choice.weekday, WeekdayForm.Initial)
                    DayToggle(
                        label = "$initial\n${choice.dayOfMonth}",
                        description = stringResource(
                            Res.string.common_joined,
                            weekdayText(choice.weekday),
                            choice.dayOfMonth.toString(),
                        ),
                        on = choice.epochDay == picked,
                        onToggle = { onPick(choice.epochDay) },
                        role = Role.RadioButton,
                        modifier = Modifier.weight(1f),
                    )
                }
                // A short week keeps its days in their columns rather than stretching them.
                repeat(DAYS_PER_WEEK - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A day to pick: accent when [on], following its state's colour. */
@Composable
private fun DayToggle(
    label: String,
    description: String,
    on: Boolean,
    onToggle: () -> Unit,
    role: Role,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    val haptics = LocalHapticFeedback.current
    val fill by animateColorAsState(if (on) palette.accent else palette.surfaceVariant, tween(Motion.STATE_MILLIS))
    val ink by animateColorAsState(if (on) palette.onAccent else palette.text, tween(Motion.STATE_MILLIS))
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = Sizes.segment)
            .pressScale(interaction)
            .clip(Corners.radius12)
            .background(fill)
            .toggleable(on, interaction, LocalIndication.current, role = role) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onToggle()
            }
            .semantics { contentDescription = description }
            .padding(vertical = Spacing.space8),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = IntervalTheme.type.label, color = ink, textAlign = TextAlign.Center)
    }
}
