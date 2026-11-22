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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
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
import dev.justinsmith.core.resources.rep_step
import dev.justinsmith.core.resources.rep_track_cd
import dev.justinsmith.interval.platform.prefersReducedMotion
import org.jetbrains.compose.resources.stringResource

/**
 * Where a diagram's rep is and how it runs, for whatever shows it to read and move: the part of the rep it is in
 * ([phase]), whether it is held and where ([heldAt]), and whether it runs at half speed ([slow]). A card that lights
 * a cue with the part, or takes the rep to one, shares it with its [ExerciseDiagram].
 */
@Stable
class RepPlayer internal constructor(private val rep: ExerciseRep, heldAt: Float?, slow: Boolean) {
    /** Where the rep is held, 0 to 1 through it; null while it runs by itself. */
    var heldAt: Float? by mutableStateOf(heldAt)
        private set

    /** Whether it runs at half its speed, for a quick lift to be followed. */
    var slow: Boolean by mutableStateOf(slow)
        private set

    /** The part of the rep it is in, if that part has a name: it changes as the rep runs or is moved. */
    var phase: RepPhase? by mutableStateOf(rep.phaseAt(heldAt ?: rep.still))
        internal set

    /** How far through the rep it has run by itself. */
    internal val position = RepPosition(heldAt ?: rep.still)

    /** Holds the rep [at] this share of the way through it. */
    fun hold(at: Float) {
        heldAt = at.coerceIn(0f, 1f)
    }

    /** Holds the rep where it is, or sets it running again from there. */
    fun toggle() {
        heldAt = if (heldAt == null) position.share else null
    }

    fun toggleSlow() {
        slow = !slow
    }

    /** Holds the rep where its next part starts ([ExerciseRep.stepAfter]), from wherever it is. */
    fun step() = hold(rep.stepAfter(heldAt ?: position.share))

    internal companion object {
        /** What a saved player has in place of where it is held, when it runs. */
        private const val RUNNING = -1f

        fun saver(rep: ExerciseRep) = listSaver<RepPlayer, Any>(
            save = { listOf(it.heldAt ?: RUNNING, it.slow) },
            restore = { RepPlayer(rep, (it[0] as Float).takeIf { at -> at >= 0f }, it[1] as Boolean) },
        )
    }
}

/** A player for [rep], running from where it stands still; what it holds and its speed are kept across a rotation. */
@Composable
fun rememberRepPlayer(rep: ExerciseRep): RepPlayer =
    rememberSaveable(rep, saver = RepPlayer.saver(rep)) { RepPlayer(rep, heldAt = null, slow = false) }

/**
 * [rep] as a diagram ([ExerciseDemoCanvas]) under the name of the part of the rep it is in and the one thing to get
 * right in it ([RepPhase.cue]), both changing as the rep goes round or as [progress] scrubs it. A rep that names no
 * part of itself is the diagram alone. The words keep the room the longest of them takes, so the diagram never
 * moves.
 *
 * With [controls] it can be handled. Beside the name, Step, which holds the rep where its next part starts; a speed
 * (full, or half for a quick lift to be followed); and Pause, which holds the rep where it is, or Play, which sets
 * it running again from there. Under the diagram a [PhaseTrack], which holds the rep where it is touched, to be
 * gone through by hand. With reduced motion it never runs, so it offers neither speed nor Play, only Step and the
 * track. Where it is and how it runs are [player]'s, which keeps what is held and the speed across a rotation: a
 * caller hands in its own to follow the rep's parts or move it, and may then draw another [rep] of the same parts
 * with it (a lift done wrong). A [progress] handed in holds it regardless.
 */
@Composable
fun ExerciseDiagram(
    rep: ExerciseRep,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    contentDescription: String? = null,
    controls: Boolean = false,
    player: RepPlayer = rememberRepPlayer(rep),
) {
    val held = progress ?: player.heldAt
    // One held from outside names its part at once, without waiting on the diagram to report it.
    val phase = if (progress == null) player.phase else rep.phaseAt(progress)
    val named = rep.phases.isNotEmpty()
    val runs = controls && !prefersReducedMotion()
    val steps = controls && named
    Column(modifier) {
        if (named || runs) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { phase?.let { PhaseBadge(it) } }
                if (steps) QuietTextButton(stringResource(Res.string.rep_step), onClick = player::step)
                if (runs) {
                    RepControls(
                        playing = held == null,
                        slow = player.slow,
                        onToggleSlow = player::toggleSlow,
                        onTogglePlaying = player::toggle,
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
            onPhase = { player.phase = it },
            position = player.position,
            speed = if (player.slow) SLOW else 1f,
        )
        if (steps) {
            PhaseTrack(
                rep,
                progress = { held ?: player.position.share },
                onScrub = player::hold,
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
