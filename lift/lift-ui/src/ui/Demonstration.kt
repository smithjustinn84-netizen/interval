package dev.justinsmith.lift.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import dev.justinsmith.interval.platform.prefersReducedMotion
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.ExerciseDemoColors
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
import dev.justinsmith.interval.ui.showingRepPlayer
import dev.justinsmith.interval.ui.surfaceButton
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Fault
import dev.justinsmith.lift.domain.faults
import dev.justinsmith.lift.ui.demo.doneWrong
import dev.justinsmith.lift.ui.demo.repCues
import dev.justinsmith.lift.ui.resources.Res
import dev.justinsmith.lift.ui.resources.demo
import dev.justinsmith.lift.ui.resources.demo_cue_jump
import dev.justinsmith.lift.ui.resources.demo_done_wrong_figure
import dev.justinsmith.lift.ui.resources.demo_each_rep
import dev.justinsmith.lift.ui.resources.demo_path
import dev.justinsmith.lift.ui.resources.demo_path_done_wrong
import dev.justinsmith.lift.ui.resources.demo_set_up
import dev.justinsmith.lift.ui.resources.demo_set_up_jump
import org.jetbrains.compose.resources.stringResource

/**
 * The unified exercise demonstration card used across Exercise Detail and Mobility MoveDetail: [rep] drawn on
 * [ExerciseDiagram] with full detail telemetry (plumb line & dashed bell trajectory when applicable), phase badge
 * and coaching cue for named-phase lifts, Step / speed / Pause controls, and a scrubbable `PhaseTrack`.
 *
 * Under the drawing (or beside it when width reaches [LiftSizes.pathBesideNotes]), the exercise's written [cues]
 * light up in sync and jump the rep when tapped, followed by any [faults] under "Watch for" with an interactive
 * "Done wrong" toggle where drawable.
 */
@Composable
fun Demonstration(
    rep: ExerciseRep,
    name: String,
    cues: List<String>,
    modifier: Modifier = Modifier,
    cueOf: Map<RepPhase, Int> = emptyMap(),
    faults: List<Fault> = emptyList(),
) {
    DemonstrationCard(rep, name, cues, cueOf, faults, modifier)
}

@Composable
private fun DemonstrationCard(
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
    val colors = ExerciseDemoColors.theme()
    val still = prefersReducedMotion()
    var wrongAt by rememberSaveable(rep) { mutableIntStateOf(faults.indexOf(showing)) }
    val wrong = faults.getOrNull(wrongAt)
    val drawn = remember(rep, wrong) { wrong?.let(rep::doneWrong) ?: rep }
    val showable = remember(rep, faults) { faults.map { rep.doneWrong(it) != null } }
    val descriptionRes = when {
        rep.showsTrajectory && wrong != null -> Res.string.demo_path_done_wrong
        rep.showsTrajectory -> Res.string.demo_path
        wrong != null -> Res.string.demo_done_wrong_figure
        else -> Res.string.demo
    }
    ListCard(modifier.showingRepPlayer(player), fill = IntervalTheme.palette.surfaceVariant) {
        PathBesideNotes(
            path = {
                Box {
                    ExerciseDiagram(
                        drawn,
                        Modifier
                            .fillMaxWidth()
                            .height(Sizes.figurePage + Sizes.iconButton * 2)
                            .padding(horizontal = Spacing.space12, vertical = Spacing.space4),
                        contentDescription = stringResource(descriptionRes, name),
                        progress = progress,
                        controls = true,
                        player = player,
                        colors = colors,
                        tracksView = false,
                    )
                    if (wrong != null) {
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
                    DemonstrationCueRows(rep, player, cues, cueOf, colors, still)
                    FaultRows(faults, showable, wrongAt, onToggle = { wrongAt = if (wrongAt == it) -1 else it })
                }
            },
        )
    }
}

@Composable
private fun DemonstrationCueRows(
    rep: ExerciseRep,
    player: RepPlayer,
    cues: List<String>,
    cueOf: Map<RepPhase, Int>,
    colors: ExerciseDemoColors,
    still: Boolean,
) {
    val current = player.phase?.let(cueOf::get) ?: player.cueIndex
    val lit = colors.of(player.beat)
    val setUp = rep.setUp?.cue?.takeIf { it in cues.indices }

    @Composable
    fun Cue(i: Int) {
        val jumpTo = when {
            i == setUp && still -> ({ player.hold(SET_UP_STILL) })

            i == setUp -> player::replaySetUp

            else -> (
                rep.phases.firstOrNull { cueOf[it.phase] == i }?.start ?: rep.startOf(i)
                )?.let { start -> { player.hold(start) } }
        }
        CueRow(
            cues[i],
            current = i == current,
            lit = lit,
            onShow = jumpTo,
            showLabel = stringResource(if (i == setUp) Res.string.demo_set_up_jump else Res.string.demo_cue_jump),
        )
    }

    if (setUp == null) {
        cues.indices.forEach { i ->
            if (i > 0) RowDivider()
            Cue(i)
        }
    } else {
        CueGroupLabel(stringResource(Res.string.demo_set_up))
        Cue(setUp)
        CueGroupLabel(stringResource(Res.string.demo_each_rep))
        cues.indices.filter { it != setUp }.forEachIndexed { n, i ->
            if (n > 0) RowDivider()
            Cue(i)
        }
    }
}

/**
 * The [path] over its [notes] with a hairline between, or beside them where there is room for both
 * ([LiftSizes.pathBesideNotes]), each half the width.
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

/** What the cues under a demonstration are grouped under: the set-up, each rep, or Watch for. */
@Composable
internal fun CueGroupLabel(text: String) {
    Text(
        text,
        style = IntervalTheme.type.meta,
        color = IntervalTheme.palette.textDim,
        modifier = Modifier.padding(start = Spacing.space16, end = Spacing.space16, top = Spacing.space12),
    )
}

/**
 * A written cue under a demonstration: a bar beside it in [lit] while [current], dim otherwise; tapping it
 * jumps the rep to its stretch via [onShow].
 */
@Composable
internal fun CueRow(line: String, current: Boolean, lit: Color, onShow: (() -> Unit)?, showLabel: String) {
    val palette = IntervalTheme.palette
    val bar by animateColorAsState(if (current) lit else palette.line, label = "cue bar")
    val ink by animateColorAsState(if (current) palette.text else palette.textDim, label = "cue ink")
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onShow == null) {
                    Modifier
                } else {
                    Modifier.surfaceButton(
                        interaction,
                        shape = RectangleShape,
                        fill = Color.Transparent,
                        onClick = onShow,
                        onClickLabel = showLabel,
                        haptic = HapticFeedbackType.SegmentTick,
                    )
                },
            )
            .semantics { selected = current }
            .height(IntrinsicSize.Min)
            .padding(Spacing.space16),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Box(Modifier.width(LiftSizes.cueBar).fillMaxHeight().clip(Corners.full).background(bar))
        Text(line, style = IntervalTheme.type.body, color = ink)
    }
}

/** With reduced motion a picked set-up shows still, halfway through it. */
private const val SET_UP_STILL = -0.5f

@PreviewFormFactors
@Composable
private fun DemonstrationPreview() {
    IntervalTheme {
        Box(Modifier.padding(Spacing.space16)) {
            DemonstrationCard(
                KettlebellReps.Swing,
                "Two-hand swing",
                listOf("Hike it high", "Snap the hips", "Let it float"),
                BuiltInExercise.TwoHandSwing.repCues,
                BuiltInExercise.TwoHandSwing.faults,
            )
        }
    }
}

@PreviewFormFactors
@Composable
private fun DemonstrationDoneWrongPreview() {
    IntervalTheme {
        Box(Modifier.padding(Spacing.space16)) {
            DemonstrationCard(
                KettlebellReps.Swing,
                "Two-hand swing",
                listOf("Hike it high", "Snap the hips", "Let it float"),
                BuiltInExercise.TwoHandSwing.repCues,
                BuiltInExercise.TwoHandSwing.faults,
                showing = Fault.SquattedHinge,
                progress = 0f,
            )
        }
    }
}
