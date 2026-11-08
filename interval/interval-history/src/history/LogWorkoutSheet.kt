package dev.justinsmith.interval.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.history_kind_interval
import dev.justinsmith.interval.history.resources.history_kind_mobility
import dev.justinsmith.interval.history.resources.history_kind_sport
import dev.justinsmith.interval.history.resources.history_log_workout
import dev.justinsmith.interval.history.resources.log_flow_which
import dev.justinsmith.interval.history.resources.log_workout_confirm
import dev.justinsmith.interval.history.resources.log_workout_text
import dev.justinsmith.interval.history.resources.notice_sport_logged
import dev.justinsmith.interval.history.resources.notice_workout_logged
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.plan.Caption
import dev.justinsmith.interval.plan.IntervalChoiceList
import dev.justinsmith.interval.plan.PickRow
import dev.justinsmith.interval.plan.PlanChoice
import dev.justinsmith.interval.plan.SportChoiceList
import dev.justinsmith.interval.plan.sportTitle
import dev.justinsmith.interval.timer.Flow
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SegmentedPicker
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.workoutDescription
import org.jetbrains.compose.resources.stringResource

/** What the log sheet offers: an interval workout (the Timer's or a preset), a mobility flow, or a day of a sport. */
private enum class LogKind { Interval, Mobility, Sport }

/**
 * Logs a workout done away from the app, or without the Timer, on [epochDay]: one of [choices] or of [flows] through
 * [onLogWorkout], or a day of a sport through [onLogSport], each with the line the notice says ("Tabata logged").
 * Nothing about when: the day is all that is known, so it goes on in full. Nothing of Lift's: a session is its sets.
 */
@Composable
internal fun LogWorkoutSheet(
    choices: List<PlanChoice>,
    flows: List<Flow>,
    epochDay: Long,
    onLogWorkout: (config: IntervalConfig, epochDay: Long, loggedText: String) -> Unit,
    onLogSport: (sport: Sport, epochDay: Long, loggedText: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = IntervalTheme.palette
    // The first choice is the Timer's; a preset is the likelier pick when there is one.
    var picked by rememberSaveable { mutableIntStateOf(if (choices.size > 1) 1 else 0) }
    var flow by rememberSaveable { mutableIntStateOf(0) }
    var sport by rememberSaveable { mutableIntStateOf(0) }
    val sportName = rememberTextFieldState()
    val kinds = LogKind.entries.filter {
        when (it) {
            LogKind.Interval -> choices.isNotEmpty()
            LogKind.Mobility -> flows.isNotEmpty()
            LogKind.Sport -> true
        }
    }
    var kind by rememberSaveable { mutableStateOf(kinds.first()) }
    val title = stringResource(Res.string.history_log_workout)
    IntervalSheet(title = title, onDismiss = onDismiss) { hide ->
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
            Text(
                title,
                style = IntervalTheme.type.title,
                color = palette.text,
                modifier = Modifier.semantics { heading() },
            )
            if (kinds.size > 1) KindPicker(kinds, kind, onPick = { kind = it })
            when (kind) {
                LogKind.Interval -> IntervalChoiceList(choices, picked, onPick = { picked = it })
                LogKind.Mobility -> FlowChoiceList(flows, flow, onPick = { flow = it })
                LogKind.Sport -> SportChoiceList(sport, sportName, onPick = { sport = it })
            }
            // The Sport side says its own word on counting.
            if (kind != LogKind.Sport) {
                Text(
                    stringResource(Res.string.log_workout_text),
                    style = IntervalTheme.type.meta,
                    color = palette.textDim,
                )
            }
            // Null while there is nothing to log: no workout to pick from, or a sport still to be named.
            val log: (() -> Unit)? = when (kind) {
                LogKind.Interval -> choices.getOrNull(picked.coerceAtMost(choices.lastIndex))?.preset?.let { preset ->
                    val logged = stringResource(Res.string.notice_workout_logged, preset.name)
                    ({ onLogWorkout(preset.config, epochDay, logged) })
                }

                LogKind.Mobility -> flows.getOrNull(flow.coerceAtMost(flows.lastIndex))?.let { picked ->
                    val logged = stringResource(Res.string.notice_workout_logged, picked.name)
                    ({ onLogWorkout(picked.config, epochDay, logged) })
                }

                LogKind.Sport -> Sport.picked(sport, sportName.text.toString())?.let { picked ->
                    val logged = stringResource(Res.string.notice_sport_logged, sportTitle(picked))
                    ({ onLogSport(picked, epochDay, logged) })
                }
            }
            AccentButton(
                stringResource(Res.string.log_workout_confirm),
                onClick = {
                    if (log != null) {
                        log()
                        hide()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = log != null,
            )
        }
    }
}

/** Interval, Mobility or Sport: the [kinds] this sheet offers. */
@Composable
private fun KindPicker(kinds: List<LogKind>, kind: LogKind, onPick: (LogKind) -> Unit) {
    SegmentedPicker(
        options = kinds,
        selected = kind,
        label = {
            when (it) {
                LogKind.Interval -> stringResource(Res.string.history_kind_interval)
                LogKind.Mobility -> stringResource(Res.string.history_kind_mobility)
                LogKind.Sport -> stringResource(Res.string.history_kind_sport)
            }
        },
        onSelect = onPick,
    )
}

/** The flows to pick from, the shipped ones then the user's own, the [picked] one checked. */
@Composable
private fun FlowChoiceList(flows: List<Flow>, picked: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
        Caption(stringResource(Res.string.log_flow_which))
        ListCard(Modifier.selectableGroup()) {
            flows.forEachIndexed { index, flow ->
                if (index > 0) RowDivider()
                PickRow(
                    name = flow.name,
                    detail = workoutDescription(flow.config),
                    selected = index == picked,
                    onPick = { onPick(index) },
                )
            }
        }
    }
}
