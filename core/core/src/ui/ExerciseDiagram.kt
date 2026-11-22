package dev.justinsmith.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.rep_phase_backswing
import dev.justinsmith.core.resources.rep_phase_bottom
import dev.justinsmith.core.resources.rep_phase_descend
import dev.justinsmith.core.resources.rep_phase_down_to_elbow
import dev.justinsmith.core.resources.rep_phase_drive
import dev.justinsmith.core.resources.rep_phase_drop
import dev.justinsmith.core.resources.rep_phase_float
import dev.justinsmith.core.resources.rep_phase_floor_press
import dev.justinsmith.core.resources.rep_phase_hand_down
import dev.justinsmith.core.resources.rep_phase_high_bridge
import dev.justinsmith.core.resources.rep_phase_high_pull
import dev.justinsmith.core.resources.rep_phase_hike_pass
import dev.justinsmith.core.resources.rep_phase_hip_snap
import dev.justinsmith.core.resources.rep_phase_lockout
import dev.justinsmith.core.resources.rep_phase_lower
import dev.justinsmith.core.resources.rep_phase_lower_bell
import dev.justinsmith.core.resources.rep_phase_press
import dev.justinsmith.core.resources.rep_phase_pull
import dev.justinsmith.core.resources.rep_phase_punch_through
import dev.justinsmith.core.resources.rep_phase_rack
import dev.justinsmith.core.resources.rep_phase_roll_down
import dev.justinsmith.core.resources.rep_phase_roll_to_elbow
import dev.justinsmith.core.resources.rep_phase_stand
import dev.justinsmith.core.resources.rep_phase_stand_up
import dev.justinsmith.core.resources.rep_phase_step_back
import dev.justinsmith.core.resources.rep_phase_sweep_through
import dev.justinsmith.core.resources.rep_phase_sweep_to_kneeling
import dev.justinsmith.core.resources.rep_phase_tall_sit
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * [rep] as a diagram ([ExerciseDemoCanvas]) under the name of the part of the rep it is in, which changes as the
 * rep goes round or as [progress] scrubs it. A rep that names no part of itself is the diagram alone. The name's
 * line keeps its room through a part with no name, so the diagram never moves.
 */
@Composable
fun ExerciseDiagram(
    rep: ExerciseRep,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    contentDescription: String? = null,
) {
    var phase by remember(rep) { mutableStateOf(rep.phaseAt(progress ?: rep.still)) }
    Column(modifier) {
        if (rep.phases.isNotEmpty()) {
            Text(
                phase?.let { stringResource(it.label) }.orEmpty(),
                style = IntervalTheme.type.caption,
                color = IntervalTheme.palette.textDim,
                maxLines = 1,
            )
        }
        ExerciseDemoCanvas(
            rep,
            Modifier.fillMaxWidth().weight(1f),
            progress = progress,
            contentDescription = contentDescription,
            onPhase = { phase = it },
        )
    }
}

/** What a part of a rep is called on screen. */
val RepPhase.label: StringResource
    get() = when (this) {
        RepPhase.HikePass -> Res.string.rep_phase_hike_pass
        RepPhase.HipSnap -> Res.string.rep_phase_hip_snap
        RepPhase.Float -> Res.string.rep_phase_float
        RepPhase.Backswing -> Res.string.rep_phase_backswing
        RepPhase.Pull -> Res.string.rep_phase_pull
        RepPhase.Rack -> Res.string.rep_phase_rack
        RepPhase.Drop -> Res.string.rep_phase_drop
        RepPhase.HighPull -> Res.string.rep_phase_high_pull
        RepPhase.PunchThrough -> Res.string.rep_phase_punch_through
        RepPhase.Lockout -> Res.string.rep_phase_lockout
        RepPhase.Press -> Res.string.rep_phase_press
        RepPhase.Lower -> Res.string.rep_phase_lower
        RepPhase.Descend -> Res.string.rep_phase_descend
        RepPhase.Bottom -> Res.string.rep_phase_bottom
        RepPhase.Drive -> Res.string.rep_phase_drive
        RepPhase.Stand -> Res.string.rep_phase_stand
        RepPhase.FloorPress -> Res.string.rep_phase_floor_press
        RepPhase.RollToElbow -> Res.string.rep_phase_roll_to_elbow
        RepPhase.TallSit -> Res.string.rep_phase_tall_sit
        RepPhase.HighBridge -> Res.string.rep_phase_high_bridge
        RepPhase.SweepToKneeling -> Res.string.rep_phase_sweep_to_kneeling
        RepPhase.StandUp -> Res.string.rep_phase_stand_up
        RepPhase.StepBack -> Res.string.rep_phase_step_back
        RepPhase.HandDown -> Res.string.rep_phase_hand_down
        RepPhase.SweepThrough -> Res.string.rep_phase_sweep_through
        RepPhase.DownToElbow -> Res.string.rep_phase_down_to_elbow
        RepPhase.RollDown -> Res.string.rep_phase_roll_down
        RepPhase.LowerBell -> Res.string.rep_phase_lower_bell
    }

/** [rep] held at each of [moments] of the way through it, two to a row and each under what it is doing there. */
@Composable
private fun DiagramSheet(rep: ExerciseRep, moments: List<Float>) {
    IntervalTheme {
        val palette = IntervalTheme.palette
        Column(
            Modifier.fillMaxSize().background(palette.background).padding(Spacing.space12),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            moments.chunked(2).forEach { row ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                    row.forEach { at ->
                        ExerciseDiagram(
                            rep,
                            Modifier.weight(1f).fillMaxHeight().clip(Corners.radius18).background(palette.surface)
                                .padding(Spacing.space8),
                            progress = at,
                        )
                    }
                }
            }
        }
    }
}

/** How far through [this] rep each stance it rests in is, on the way to where it stands still: a get-up's steps. */
private fun ExerciseRep.steps(): List<Float> = keyframes.indices
    .filter { it > 0 && !keyframes[it].passing }
    .distinctBy { keyframes[it].pose }
    .map(::timeOf)

/** The moments a preview holds a rep at when it shows it start to finish. */
private const val MOMENTS = 8

@PreviewFormFactors
@Composable
private fun ExerciseDiagramGetUpPreview() {
    DiagramSheet(KettlebellReps.GetUp, KettlebellReps.GetUp.steps())
}

@PreviewFormFactors
@Composable
private fun ExerciseDiagramSnatchPreview() {
    DiagramSheet(KettlebellReps.Snatch, List(MOMENTS) { it.toFloat() / MOMENTS })
}
