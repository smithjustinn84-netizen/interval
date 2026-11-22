package dev.justinsmith.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.rep_pause_cd
import dev.justinsmith.core.resources.rep_play_cd
import dev.justinsmith.core.resources.rep_speed_full
import dev.justinsmith.core.resources.rep_speed_full_cd
import dev.justinsmith.core.resources.rep_speed_slow
import dev.justinsmith.core.resources.rep_speed_slow_cd
import dev.justinsmith.core.resources.rep_track_cd
import dev.justinsmith.interval.platform.prefersReducedMotion
import org.jetbrains.compose.resources.stringResource

/**
 * [rep] as a diagram ([ExerciseDemoCanvas]) under the name of the part of the rep it is in and the one thing to get
 * right in it ([RepPhase.cue]), both changing as the rep goes round or as [progress] scrubs it. A rep that names no
 * part of itself is the diagram alone. The words keep the room the longest of them takes, so the diagram never
 * moves.
 *
 * With [controls] it can be handled. Beside the name, a speed (full, or half for a quick lift to be followed) and
 * Pause, which holds the rep where it is, or Play, which sets it running again from there; under the diagram a
 * [PhaseTrack], which holds the rep where it is touched, to be gone through by hand. With reduced motion it never
 * runs, so it offers neither speed nor Play, only the track. What is held and how fast it runs are kept across a
 * rotation. A [progress] handed in holds it regardless.
 */
@Composable
fun ExerciseDiagram(
    rep: ExerciseRep,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    contentDescription: String? = null,
    controls: Boolean = false,
) {
    var heldAt by rememberSaveable(rep) { mutableStateOf<Float?>(null) }
    var slow by rememberSaveable(rep) { mutableStateOf(false) }
    val held = progress ?: heldAt
    val position = rememberRepPosition(rep)
    var phase by remember(rep) { mutableStateOf(rep.phaseAt(held ?: rep.still)) }
    val named = rep.phases.isNotEmpty()
    val runs = controls && !prefersReducedMotion()
    Column(modifier) {
        if (named || runs) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { phase?.let { PhaseBadge(it) } }
                if (runs) {
                    RepControls(
                        playing = held == null,
                        slow = slow,
                        onToggleSlow = { slow = !slow },
                        onTogglePlaying = { heldAt = if (heldAt == null) position.share else null },
                    )
                }
            }
        }
        if (named) PhaseCue(rep, phase)
        ExerciseDemoCanvas(
            rep,
            Modifier.fillMaxWidth().weight(1f),
            progress = held,
            contentDescription = contentDescription,
            onPhase = { phase = it },
            position = position,
            speed = if (slow) SLOW else 1f,
        )
        if (controls && named) {
            PhaseTrack(
                rep,
                progress = { held ?: position.share },
                onScrub = { heldAt = it },
                label = stringResource(Res.string.rep_track_cd),
            )
        }
    }
}

/** The name of the part of a rep a diagram is in, as a tag in the bell's colour. */
@Composable
private fun PhaseBadge(phase: RepPhase) {
    val palette = IntervalTheme.palette
    Text(
        stringResource(phase.label),
        style = IntervalTheme.type.caption,
        color = palette.accent,
        maxLines = 1,
        modifier = Modifier
            .clip(Corners.full)
            .background(palette.accentTint)
            .padding(horizontal = Spacing.space8, vertical = Spacing.space4),
    )
}

/**
 * The one thing to get right in [phase], in the room the longest of [rep]'s cues takes at this width and text size:
 * the others are laid out under it, unseen and unread, so the diagram under them stays where it is as they change.
 */
@Composable
private fun PhaseCue(rep: ExerciseRep, phase: RepPhase?) {
    val palette = IntervalTheme.palette
    val others = remember(rep) { rep.phases.map { it.phase }.distinct() }
    Box(Modifier.padding(top = Spacing.space4)) {
        others.forEach { other ->
            Text(
                stringResource(other.cue),
                style = IntervalTheme.type.meta,
                modifier = Modifier.drawWithContent {}.clearAndSetSemantics {},
            )
        }
        phase?.let { Text(stringResource(it.cue), style = IntervalTheme.type.meta, color = palette.text) }
    }
}

/**
 * A running diagram's controls: its speed, which a tap changes ([onToggleSlow]), then Pause while it is [playing]
 * and Play while it is held ([onTogglePlaying]).
 */
@Composable
private fun RepControls(playing: Boolean, slow: Boolean, onToggleSlow: () -> Unit, onTogglePlaying: () -> Unit) {
    val speed = stringResource(if (slow) Res.string.rep_speed_slow_cd else Res.string.rep_speed_full_cd)
    QuietTextButton(
        stringResource(if (slow) Res.string.rep_speed_slow else Res.string.rep_speed_full),
        onClick = onToggleSlow,
        Modifier.semantics { contentDescription = speed },
    )
    IconAction(
        if (playing) IntervalIcons.Pause else IntervalIcons.Play,
        stringResource(if (playing) Res.string.rep_pause_cd else Res.string.rep_play_cd),
        onClick = onTogglePlaying,
    )
}

/** How fast a diagram runs slowed: half speed, as Lift's figure does. */
private const val SLOW = 0.5f

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
private fun ExerciseDiagramScrubbablePreview() {
    IntervalTheme {
        val palette = IntervalTheme.palette
        Column(
            Modifier.fillMaxSize().background(palette.background).padding(Spacing.space12),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            listOf(KettlebellReps.GetUp, KettlebellReps.Swing).forEach { rep ->
                ExerciseDiagram(
                    rep,
                    Modifier.weight(1f).fillMaxWidth().clip(Corners.radius18).background(palette.surface)
                        .padding(Spacing.space12),
                    progress = rep.still,
                    controls = true,
                )
            }
        }
    }
}

@PreviewFormFactors
@Composable
private fun ExerciseDiagramSnatchPreview() {
    DiagramSheet(KettlebellReps.Snatch, List(MOMENTS) { it.toFloat() / MOMENTS })
}
