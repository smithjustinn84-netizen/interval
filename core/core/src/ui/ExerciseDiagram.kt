package dev.justinsmith.interval.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.rep_pause_cd
import dev.justinsmith.core.resources.rep_pause_rep_cd
import dev.justinsmith.core.resources.rep_play_cd
import dev.justinsmith.core.resources.rep_play_rep_cd
import dev.justinsmith.core.resources.rep_speed_full
import dev.justinsmith.core.resources.rep_speed_full_cd
import dev.justinsmith.core.resources.rep_speed_slow
import dev.justinsmith.core.resources.rep_speed_slow_cd
import dev.justinsmith.core.resources.rep_step
import dev.justinsmith.core.resources.rep_track_cd
import dev.justinsmith.core.resources.rep_track_rep_cd
import dev.justinsmith.interval.platform.prefersReducedMotion
import org.jetbrains.compose.resources.stringResource

/**
 * Where a diagram's rep is and how it runs, for whatever shows it to read and move: the part of the rep it is in
 * ([phase], [span]), whether it is held and where ([heldAt]), and whether it runs at half speed ([slow]). A card
 * that lights a cue with the part, or takes the rep to one, shares it with its [ExerciseDiagram].
 */
@Stable
class RepPlayer internal constructor(
    private val rep: ExerciseRep,
    heldAt: Float?,
    slow: Boolean,
    initialShare: Float = heldAt ?: rep.still,
) {
    /** Where the rep is held, [ExerciseRep.earliest] to 1; null while it runs by itself. */
    var heldAt: Float? by mutableStateOf(heldAt)
        private set

    /** Whether it runs at half its speed, for a quick lift to be followed. */
    var slow: Boolean by mutableStateOf(slow)
        private set

    /** The named phase it is in, if this rep uses [RepPhase]s. */
    var phase: RepPhase? by mutableStateOf(rep.phaseAt(initialShare))
        internal set

    /** The timeline stretch it is in (null while setting up). */
    var span: RepSpan? by mutableStateOf(rep.spanAt(initialShare))
        internal set

    /** Whether this player's view is visible on screen (scrolled-away animations pause without reading as paused). */
    internal var inView: Boolean by mutableStateOf(true)

    /** Target share when smoothly gliding to a picked cue via [show]. */
    internal var heading: Float? by mutableStateOf(null)
        private set

    /** How far through the rep it has run by itself. */
    val position = RepPosition(initialShare)

    /** Whether the rep is currently running autonomously. */
    val playing: Boolean get() = heldAt == null && heading == null

    /** Current share through the rep (or set-up below 0). */
    val progress: Float get() = heldAt ?: position.share

    /** Current effort beat ([RepBeat.Set] while setting up). */
    val beat: RepBeat get() = if (progress < 0f && rep.setUp != null) RepBeat.Set else span?.beat ?: RepBeat.Drive

    /** Current written cue index, if the rep tags stretches with cues or has a set-up cue. */
    val cueIndex: Int? get() = if (progress < 0f && rep.setUp != null) rep.setUp.cue else span?.cueIndex

    /** Holds the rep immediately [at] this share of the way through it. */
    fun hold(at: Float) {
        heading = null
        val clamped = at.coerceIn(rep.earliest, 1f)
        heldAt = clamped
        position.share = clamped
        phase = rep.phaseAt(clamped)
        span = rep.spanAt(clamped)
    }

    /** Stops the rep and glides smoothly to [at] over [Motion.CONTENT_MILLIS] (immediate under reduced motion). */
    fun show(at: Float) {
        val clamped = at.coerceIn(rep.earliest, 1f)
        heldAt = progress
        heading = clamped
    }

    /** Replays from the start of set-up into the looping rep. */
    fun replaySetUp() {
        heading = null
        heldAt = null
        position.share = rep.earliest
        phase = rep.phaseAt(rep.earliest)
        span = rep.spanAt(rep.earliest)
    }

    /** Holds the rep where it is. */
    fun pause() {
        heading = null
        heldAt = position.share
    }

    /** Resumes running from where it is held. */
    fun play() {
        heading = null
        heldAt = null
    }

    /** Holds the rep where it is, or sets it running again from there. */
    fun toggle() {
        heading = null
        heldAt = if (heldAt == null) position.share else null
    }

    fun toggleSlow() {
        slow = !slow
    }

    /** Holds the rep where its next stretch starts ([ExerciseRep.stepAfter]), from wherever it is. */
    fun step() = hold(rep.stepAfter(heading ?: heldAt ?: position.share))

    internal fun glideTo(at: Float) {
        heldAt = at
        position.share = at
        phase = rep.phaseAt(at)
        span = rep.spanAt(at)
    }

    internal fun arrive() {
        val target = heading ?: return
        heading = null
        hold(target)
    }

    internal companion object {
        /** What a saved player has in place of where it is held, when it runs. */
        private const val RUNNING = -2f

        fun saver(rep: ExerciseRep) = listSaver<RepPlayer, Any>(
            save = { listOf(it.heldAt ?: RUNNING, it.slow) },
            restore = {
                val savedHeld = (it[0] as Float).takeIf { at -> at >= rep.earliest }
                RepPlayer(rep, savedHeld, it[1] as Boolean)
            },
        )
    }
}

/**
 * A player for [rep], running from set-up (if any) or where it stands still; held position and speed survive
 * configuration changes. Drives smooth cue-jump glides requested through [RepPlayer.show].
 */
@Composable
fun rememberRepPlayer(rep: ExerciseRep): RepPlayer {
    val still = prefersReducedMotion()
    val player = rememberSaveable(rep, saver = RepPlayer.saver(rep)) {
        val start = if (!still && rep.setUp != null) rep.earliest else rep.still
        RepPlayer(rep, heldAt = null, slow = false, initialShare = start)
    }
    LaunchedEffect(player, still, player.heading) {
        val to = player.heading ?: return@LaunchedEffect
        if (!still) {
            val from = player.progress
            animate(0f, 1f, animationSpec = tween(Motion.CONTENT_MILLIS, easing = StandardEasing)) { share, _ ->
                player.glideTo(placeBetween(from, to, share))
            }
        }
        player.arrive()
    }
    return player
}

/** Reports visibility of the enclosing card or canvas to [player] so scrolled-off animations pause frames. */
fun Modifier.showingRepPlayer(player: RepPlayer): Modifier =
    onVisibilityChanged(minFractionVisible = 0f) { player.inView = it }

/**
 * [rep] as a diagram ([ExerciseDemoCanvas]) under the name of the part of the rep it is in and its short coaching
 * cue ([RepPhase.cue]) when the rep names phases, with optional [controls] (Step, 1×/0.5× speed, Pause/Play, and
 * [PhaseTrack] scrubber).
 */
@Composable
fun ExerciseDiagram(
    rep: ExerciseRep,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    contentDescription: String? = null,
    controls: Boolean = false,
    player: RepPlayer = rememberRepPlayer(rep),
    colors: ExerciseDemoColors = ExerciseDemoColors.theme(),
    tracksView: Boolean = true,
) {
    val held = progress ?: player.heldAt
    val phase = if (progress == null) player.phase else rep.phaseAt(progress)
    val named = rep.phases.isNotEmpty()
    val runs = controls && !prefersReducedMotion()
    val steps = controls && (named || rep.spans.isNotEmpty())
    Column(modifier) {
        if (named || runs || steps) {
            DiagramHeader(phase, steps, runs, held == null, rep.showsTrajectory, player)
        }
        if (named) PhaseCue(rep, phase)
        ExerciseDemoCanvas(
            rep,
            Modifier.fillMaxWidth().weight(1f),
            progress = held,
            colors = colors,
            contentDescription = contentDescription,
            onPhase = { player.phase = it },
            onSpan = { player.span = it },
            position = player.position,
            speed = if (player.slow) SLOW else 1f,
            mode = DemoDisplayMode.Detail,
            tracksView = tracksView,
        )
        if (steps) {
            PhaseTrack(
                rep,
                progress = { held ?: player.position.share },
                onScrub = player::hold,
                label = stringResource(
                    if (rep.showsTrajectory) Res.string.rep_track_cd else Res.string.rep_track_rep_cd,
                ),
                colors = colors,
            )
        }
    }
}

@Composable
private fun DiagramHeader(
    phase: RepPhase?,
    steps: Boolean,
    runs: Boolean,
    playing: Boolean,
    showsPath: Boolean,
    player: RepPlayer,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { phase?.let { PhaseBadge(it) } }
        if (steps) QuietTextButton(stringResource(Res.string.rep_step), onClick = player::step)
        if (runs) {
            RepControls(
                playing = playing,
                slow = player.slow,
                showsPath = showsPath,
                onToggleSlow = player::toggleSlow,
                onTogglePlaying = player::toggle,
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
private fun RepControls(
    playing: Boolean,
    slow: Boolean,
    showsPath: Boolean,
    onToggleSlow: () -> Unit,
    onTogglePlaying: () -> Unit,
) {
    val speed = stringResource(if (slow) Res.string.rep_speed_slow_cd else Res.string.rep_speed_full_cd)
    QuietTextButton(
        stringResource(if (slow) Res.string.rep_speed_slow else Res.string.rep_speed_full),
        onClick = onToggleSlow,
        Modifier.semantics { contentDescription = speed },
    )
    val playPauseLabel = when {
        playing && showsPath -> Res.string.rep_pause_cd
        playing -> Res.string.rep_pause_rep_cd
        showsPath -> Res.string.rep_play_cd
        else -> Res.string.rep_play_rep_cd
    }
    IconAction(
        if (playing) IntervalIcons.Pause else IntervalIcons.Play,
        stringResource(playPauseLabel),
        onClick = onTogglePlaying,
    )
}

/** How fast a diagram runs slowed: half speed. */
private const val SLOW = 0.5f

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

private fun ExerciseRep.steps(): List<Float> = keyframes.indices
    .filter { it > 0 && !keyframes[it].passing }
    .distinctBy { keyframes[it].pose }
    .map(::timeOf)

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
