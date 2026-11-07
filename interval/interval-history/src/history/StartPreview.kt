package dev.justinsmith.interval.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.history_kind_conditioning
import dev.justinsmith.interval.history.resources.history_kind_mobility
import dev.justinsmith.interval.history.resources.history_kind_strength
import dev.justinsmith.interval.history.resources.history_lift_any
import dev.justinsmith.interval.history.resources.history_lift_session
import dev.justinsmith.interval.history.resources.history_start
import dev.justinsmith.interval.history.resources.home_start_day
import dev.justinsmith.interval.history.resources.home_start_session
import dev.justinsmith.interval.history.resources.preview_plan_later
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.plan.AddToPlanSheet
import dev.justinsmith.interval.plan.LiftChoices
import dev.justinsmith.interval.plan.PlanChoice
import dev.justinsmith.interval.plan.PlanRepeat
import dev.justinsmith.interval.plan.dayChoices
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.Flow
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.weekdayOf
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.FlowMoves
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StartButton
import dev.justinsmith.interval.ui.Timeline
import dev.justinsmith.interval.ui.configDescription
import dev.justinsmith.interval.ui.joined
import dev.justinsmith.interval.ui.movesText
import dev.justinsmith.lift.domain.ProgramDay
import dev.justinsmith.lift.ui.blockLine
import dev.justinsmith.lift.ui.estimateLine
import dev.justinsmith.lift.ui.liftName
import dev.justinsmith.lift.ui.programDayLine
import dev.justinsmith.lift.ui.programName
import org.jetbrains.compose.resources.stringResource

/** What a look before Start shows: an interval workout or a flow, or a session on Lift. */
sealed interface StartPreview {
    /** [preset] as it runs, [totalSeconds] long from the Timer as it stands; a [flow] shows its moves. */
    data class Workout(val preset: Preset, val totalSeconds: Int, val flow: Flow? = null) : StartPreview

    /** The program's [day], about [minutes] long, or with none a free session. */
    data class Lift(val day: ProgramDay?, val minutes: Int? = null) : StartPreview
}

/** What [StartPreviewSheet] does: start it, or plan it for later. */
class PreviewActions(
    val onStartWorkout: (Preset) -> Unit = {},
    val onStartLift: (ProgramDay?) -> Unit = {},
    val onPlanLater: (StartPreview) -> Unit = {},
)

/**
 * A look before Start: what it is and about how long, its name, then what is in it (a session's lifts with their
 * sets, a flow's moves, a workout's timeline), with Start under them and the way to plan it for later.
 */
@Composable
fun StartPreviewSheet(preview: StartPreview, actions: PreviewActions, onDismiss: () -> Unit) {
    val palette = IntervalTheme.palette
    val title = previewTitle(preview)
    IntervalSheet(title = title, onDismiss = onDismiss) { hide ->
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
            ) {
                Box(
                    Modifier
                        .size(IntervalSizes.kindSwatch, Sizes.progressBar)
                        .clip(Corners.full)
                        .background(kindColor(preview.kind)),
                )
                Text(previewCaption(preview), style = IntervalTheme.type.caption, color = palette.textDim)
            }
            Text(
                title,
                style = IntervalTheme.type.title,
                color = palette.text,
                modifier = Modifier.semantics { heading() },
            )
        }
        // A flow's moves follow its Start, as on the Mobility pillar: ten of them are a long way to scroll for it.
        val flow = (preview as? StartPreview.Workout)?.flow
        if (flow == null) {
            when (preview) {
                is StartPreview.Lift -> LiftContents(preview.day)
                is StartPreview.Workout -> WorkoutContents(preview)
            }
        }
        PreviewButtons(preview, actions, hide)
        flow?.let { FlowMoves(it.moves) }
    }
}

/** Start, then Plan for later, each closing the sheet first. */
@Composable
private fun PreviewButtons(preview: StartPreview, actions: PreviewActions, hide: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        StartButton(
            onClick = {
                hide()
                when (preview) {
                    is StartPreview.Lift -> actions.onStartLift(preview.day)
                    is StartPreview.Workout -> actions.onStartWorkout(preview.preset)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = when (preview) {
                is StartPreview.Lift -> preview.day?.let { stringResource(Res.string.home_start_day, it.number) }
                    ?: stringResource(Res.string.home_start_session)

                is StartPreview.Workout -> stringResource(Res.string.history_start)
            },
        )
        SecondaryButton(
            stringResource(Res.string.preview_plan_later),
            onClick = {
                hide()
                actions.onPlanLater(preview)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The kind a preview is of, for its mark. */
val StartPreview.kind: WorkoutKind
    get() = when (this) {
        is StartPreview.Lift -> WorkoutKind.Strength

        is StartPreview.Workout ->
            if (preset.config.activity == Activity.Mobility) WorkoutKind.Mobility else WorkoutKind.Conditioning
    }

/** What the add sheet offers when a preview is planned for later: this workout alone. */
fun StartPreview.planChoices(): List<PlanChoice> = when (this) {
    is StartPreview.Lift -> emptyList()
    is StartPreview.Workout -> listOf(PlanChoice(preset))
}

@Composable
private fun previewTitle(preview: StartPreview): String = when (preview) {
    is StartPreview.Lift -> preview.day?.let { programName(it.program) }
        ?: stringResource(Res.string.history_lift_session)

    is StartPreview.Workout -> preview.preset.name
}

/** "Strength · about 20 min", "Conditioning · 9:40", "Mobility · 6 moves · 8:00". */
@Composable
private fun previewCaption(preview: StartPreview): String {
    val kind = stringResource(
        when (preview.kind) {
            WorkoutKind.Strength -> Res.string.history_kind_strength
            WorkoutKind.Mobility -> Res.string.history_kind_mobility
            else -> Res.string.history_kind_conditioning
        },
    )
    return when (preview) {
        is StartPreview.Lift -> joined(listOfNotNull(kind, preview.minutes?.let { estimateLine(it) }))

        is StartPreview.Workout -> joined(
            listOfNotNull(kind, preview.flow?.let { movesText(it) }, formatClock(preview.totalSeconds)),
        )
    }
}

/** The day's lifts, each with its sets: "Front squat · 5 × 5 · 2 × 16 kg". */
@Composable
private fun LiftContents(day: ProgramDay?) {
    val palette = IntervalTheme.palette
    if (day == null) {
        Text(stringResource(Res.string.history_lift_any), style = IntervalTheme.type.meta, color = palette.textDim)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        Text(programDayLine(day), style = IntervalTheme.type.meta, color = palette.textDim)
        ListCard(fill = palette.surfaceVariant) {
            day.blocks.forEachIndexed { index, block ->
                if (index > 0) RowDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.listRow)
                        .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
                ) {
                    Text(
                        liftName(block.exercise, block.double),
                        style = IntervalTheme.type.label,
                        color = palette.text,
                        modifier = Modifier.weight(1f),
                    )
                    Text(blockLine(block), style = IntervalTheme.type.label, color = palette.textDim)
                }
            }
        }
    }
}

/** A workout's shape in words over its timeline. */
@Composable
private fun WorkoutContents(preview: StartPreview.Workout) {
    val palette = IntervalTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        Text(configDescription(preview.preset.config), style = IntervalTheme.type.meta, color = palette.textDim)
        Timeline(preview.preset.config, Modifier.fillMaxWidth())
    }
}

/**
 * A [preview] open, and, once Plan for later is tapped, the add sheet with it alone to plan, from [today] on and on
 * Lift's side for a session. [onDone] as either closes, which the caller takes the preview off for.
 */
@Composable
fun PreviewHost(
    preview: StartPreview,
    today: Long,
    onStartWorkout: (Preset) -> Unit,
    onStartLift: (ProgramDay?) -> Unit,
    onPlanWeekly: (PlanWorkout, Set<Int>) -> Unit,
    onPlanOnce: (PlanWorkout, Long) -> Unit,
    onDone: () -> Unit,
) {
    var planning by rememberSaveable { mutableStateOf(false) }
    if (planning) {
        val lift = preview as? StartPreview.Lift
        AddToPlanSheet(
            choices = preview.planChoices(),
            days = dayChoices(today),
            onPlanWeekly = onPlanWeekly,
            onPlanOnce = onPlanOnce,
            onDismiss = onDone,
            repeat = PlanRepeat.Once,
            weekday = weekdayOf(today + 1),
            epochDay = today + 1,
            lift = lift?.let { LiftChoices(it.day) },
            startOnLift = lift != null,
        )
    } else {
        StartPreviewSheet(
            preview,
            PreviewActions(onStartWorkout, onStartLift, onPlanLater = { planning = true }),
            // Closing to plan it for later leaves the host up, for the add sheet.
            onDismiss = { if (!planning) onDone() },
        )
    }
}

/** What a look before [this] shows: none for a sport, which nothing starts, or a session to resume. */
fun UpNextItem.preview(): StartPreview? = when (this) {
    is UpNextItem.Planned -> StartPreview.Workout(Preset(name, config), config.totalSeconds)
    is UpNextItem.Lift -> if (resume) null else StartPreview.Lift(programDay, minutes)
    is UpNextItem.Flow -> StartPreview.Workout(Preset(flow.name, flow.config), totalSeconds, flow)
    is UpNextItem.SportDay -> null
}
