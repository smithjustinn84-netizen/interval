package dev.justinsmith.lift.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import dev.justinsmith.interval.ui.ExerciseDiagram
import dev.justinsmith.interval.ui.ExerciseRep
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.KettlebellReps
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.RepPhase
import dev.justinsmith.interval.ui.RepPlayer
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.rememberRepPlayer
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Fault
import dev.justinsmith.lift.domain.faults
import dev.justinsmith.lift.ui.demo.diagramCues
import dev.justinsmith.lift.ui.demo.doneWrong
import dev.justinsmith.lift.ui.resources.Res
import dev.justinsmith.lift.ui.resources.demo_cue_jump
import dev.justinsmith.lift.ui.resources.demo_path
import dev.justinsmith.lift.ui.resources.demo_path_done_wrong
import org.jetbrains.compose.resources.stringResource

/**
 * A flagship lift's card, in place of the figure's ([Demonstration]): [rep] drawn from the side with the path its
 * bell takes, under the name of the part of the rep it is in and the one thing to get right there, with Step, a
 * speed and Pause beside the name and a bar of the rep's parts under the drawing to go through it by hand
 * ([ExerciseDiagram]). Under that the lift's written [cues], the one the part belongs to lit ([cueOf], a cue's
 * place for each part), each tapped to hold the rep where its first part starts; and the [faults] the lift is
 * prone to, each by the words that put it right, one the diagram can show tapped to have it done wrong, marked so,
 * and tapped again to have it done right. One player runs it all, so pausing, stepping or dragging moves the
 * drawing, the bar and the lit cue together.
 *
 * Where the card is wide enough for both ([LiftSizes.pathBesideNotes]: a sheet across a phone on its side, never
 * the side sheet a tablet opens the page in), the cues and faults stand beside the drawing rather than under it.
 */
@Composable
fun PathDemonstration(
    rep: ExerciseRep,
    name: String,
    cues: List<String>,
    modifier: Modifier = Modifier,
    cueOf: Map<RepPhase, Int> = emptyMap(),
    faults: List<Fault> = emptyList(),
) {
    PathCard(rep, name, cues, cueOf, faults, modifier)
}

@Composable
private fun PathCard(
    rep: ExerciseRep,
    name: String,
    cues: List<String>,
    cueOf: Map<RepPhase, Int>,
    faults: List<Fault>,
    modifier: Modifier = Modifier,
    showing: Fault? = null,
    progress: Float? = null,
) {
    val player = rememberRepPlayer(rep)
    // The fault shown, by its place among the lift's; -1 for none. The one player runs either rep.
    var wrongAt by rememberSaveable(rep) { mutableIntStateOf(faults.indexOf(showing)) }
    val wrong = faults.getOrNull(wrongAt)
    val drawn = remember(rep, wrong) { wrong?.let(rep::doneWrong) ?: rep }
    val showable = remember(rep, faults) { faults.map { rep.doneWrong(it) != null } }
    ListCard(modifier, fill = IntervalTheme.palette.surfaceVariant) {
        PathBesideNotes(
            path = {
                Box {
                    ExerciseDiagram(
                        drawn,
                        Modifier
                            .fillMaxWidth()
                            .height(Sizes.figurePage + Sizes.iconButton * 2)
                            .padding(horizontal = Spacing.space12, vertical = Spacing.space4),
                        contentDescription = stringResource(
                            if (wrong == null) Res.string.demo_path else Res.string.demo_path_done_wrong,
                            name,
                        ),
                        progress = progress,
                        controls = true,
                        player = player,
                    )
                    if (wrong != null) {
                        // Over the drawing's corner, clear of the bar under it.
                        DoneWrongMark(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = Spacing.space12, bottom = Sizes.iconButton + Spacing.space8),
                        )
                    }
                }
            },
            notes = {
                Column {
                    PathCueRows(rep, player, cues, cueOf)
                    FaultRows(faults, showable, wrongAt, onToggle = { wrongAt = if (wrongAt == it) -1 else it })
                }
            },
        )
    }
}

/**
 * The lift's written [cues] under its diagram, a divider between them: each lit in the bell's colour while
 * [player]'s rep is in a part of its ([cueOf]), and tapped to hold the rep where the first of its parts starts.
 */
@Composable
private fun PathCueRows(rep: ExerciseRep, player: RepPlayer, cues: List<String>, cueOf: Map<RepPhase, Int>) {
    val current = player.phase?.let(cueOf::get)
    cues.forEachIndexed { i, line ->
        if (i > 0) RowDivider()
        CueRow(
            line,
            current = i == current,
            lit = IntervalTheme.palette.accent,
            onShow = rep.phases.firstOrNull { cueOf[it.phase] == i }?.let { part -> { player.hold(part.start) } },
            showLabel = stringResource(Res.string.demo_cue_jump),
        )
    }
}

/**
 * The [path] over its [notes] with a hairline between, or beside them where there is room for both
 * ([LiftSizes.pathBesideNotes]), each half the width. One layout of three slots, so nothing is composed twice.
 */
@Composable
private fun PathBesideNotes(path: @Composable () -> Unit, notes: @Composable () -> Unit) {
    Layout(contents = listOf(path, { RowDivider() }, notes)) { (paths, dividers, rest), constraints ->
        val width = constraints.maxWidth
        val beside = constraints.hasBoundedWidth && width >= LiftSizes.pathBesideNotes.roundToPx()
        val each = Constraints.fixedWidth(if (beside) width / 2 else width)
        val drawing = paths.first().measure(each)
        val divider = dividers.first().measure(each)
        val others = rest.first().measure(each)
        // Under the drawing the notes start with a hairline, unless there are none; beside it they need none.
        val between = if (beside || others.height == 0) 0 else divider.height
        val height = if (beside) maxOf(drawing.height, others.height) else drawing.height + between + others.height
        layout(width, height) {
            drawing.placeRelative(0, 0)
            if (beside) {
                others.placeRelative(drawing.width, 0)
            } else {
                if (between > 0) divider.placeRelative(0, drawing.height)
                others.placeRelative(0, drawing.height + between)
            }
        }
    }
}

/** A swing's card: its path over its cues, the snap's lit, and what to watch for. */
@PreviewFormFactors
@Composable
private fun PathDemonstrationPreview() {
    IntervalTheme {
        Box(Modifier.padding(Spacing.space16)) {
            PathCard(
                KettlebellReps.Swing,
                "Two-hand swing",
                listOf("Hike it high", "Snap the hips", "Let it float"),
                BuiltInExercise.TwoHandSwing.diagramCues,
                BuiltInExercise.TwoHandSwing.faults,
            )
        }
    }
}

/** The card showing a swing squatted, its fault lit under the cues. */
@PreviewFormFactors
@Composable
private fun PathDemonstrationDoneWrongPreview() {
    IntervalTheme {
        Box(Modifier.padding(Spacing.space16)) {
            PathCard(
                KettlebellReps.Swing,
                "Two-hand swing",
                listOf("Hike it high", "Snap the hips", "Let it float"),
                BuiltInExercise.TwoHandSwing.diagramCues,
                BuiltInExercise.TwoHandSwing.faults,
                showing = Fault.SquattedHinge,
                // In the hinge, where squatting it shows.
                progress = 0f,
            )
        }
    }
}
