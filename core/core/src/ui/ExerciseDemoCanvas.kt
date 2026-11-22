package dev.justinsmith.interval.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.rep_bowing
import dev.justinsmith.core.resources.rep_raising_bell
import dev.justinsmith.interval.platform.prefersReducedMotion
import kotlinx.coroutines.flow.filterNotNull
import org.jetbrains.compose.resources.stringResource

// Unified exercise animation canvas: accurate fixed-proportion BodyPose kinematics rendered with an organic
// contoured silhouette, sculpted equipment, ground shadow, breathing, drive glow, and (in Detail mode) plumb-line
// & trajectory telemetry.

/**
 * How much telemetry and framing an [ExerciseDemoCanvas] renders:
 * - [Detail]: Exercise detail page — full plumb line, datum ticks, dashed trajectory & keyframe rings, trail, glow.
 * - [Compact]: Session & mobility bands — clean animated figure, trail, ground shadow, glow, and optional celebration.
 * - [Thumb]: List row thumbnail — static close-up still at [ExerciseRep.still], no telemetry or trail.
 * - [Ghost]: Beside a running timer — the animated figure and what it holds as one thin outline in the quiet
 *   [ExerciseDemoColors.far] ink, over a hairline floor: no fill, shading, shadow, glow, trail or celebration.
 */
enum class DemoDisplayMode { Detail, Compact, Thumb, Ghost }

/**
 * The colours a demonstration is drawn in: the [body], its [far] leg and arm, the [joint] contrast token, the
 * [reference] lines (floor & plumb line), the primary [bell] accent, the ground [shadow], and each [RepBeat]'s
 * tint ([set], [drive], [ease]).
 */
@Immutable
data class ExerciseDemoColors(
    val body: Color,
    val far: Color,
    val joint: Color,
    val reference: Color,
    val bell: Color,
    val shadow: Color = Color.Black.copy(alpha = LIGHT_SHADOW_ALPHA),
    val set: Color = bell,
    val drive: Color = bell,
    val ease: Color = bell,
) {
    /** Colour for [beat]: set-up, effort drive, or easing back. */
    fun of(beat: RepBeat): Color = when (beat) {
        RepBeat.Set -> set
        RepBeat.Drive -> drive
        RepBeat.Ease -> ease
    }

    /**
     * The weight/mat colour at [progress] through [rep], gliding smoothly across stretch boundaries rather than
     * snapping on a single frame.
     */
    fun at(rep: ExerciseRep, progress: Float): Color {
        if (progress < 0f && rep.setUp != null) return set
        val spans = rep.spans
        if (spans.isEmpty()) return bell
        val at = if (progress == 1f) 1f else looped(progress)
        val index = spans.indexOfFirst { at <= it.until }.let { if (it < 0) spans.lastIndex else it }
        val current = spans[index].beat
        val blend = (BEAT_BLEND_SECONDS / rep.seconds).coerceAtMost(MAX_BEAT_BLEND)
        val elapsed = at - spans[index].from
        val previous = when {
            index > 0 -> spans[index - 1].beat
            rep.setUp != null -> RepBeat.Set
            else -> spans.last().beat
        }
        return if (elapsed >= blend || previous == current) {
            of(current)
        } else {
            lerp(of(previous), of(current), (elapsed / blend).coerceIn(0f, 1f))
        }
    }

    companion object {
        internal fun of(palette: IntervalPalette): ExerciseDemoColors {
            val dark = palette.text.luminance() > MID_LUMINANCE
            return ExerciseDemoColors(
                body = palette.text,
                far = palette.textDim,
                joint = palette.background,
                reference = palette.line,
                bell = palette.accent,
                shadow = Color.Black.copy(alpha = if (dark) DARK_SHADOW_ALPHA else LIGHT_SHADOW_ALPHA),
                set = palette.prepMark,
                drive = palette.workMark,
                ease = palette.restMark,
            )
        }

        /** The theme's setup surface colours. */
        @Composable
        @ReadOnlyComposable
        fun theme(): ExerciseDemoColors = of(IntervalTheme.palette)

        /** The dark pine workout screen's colours, matching the chronograph ring. */
        @Composable
        @ReadOnlyComposable
        fun workout(): ExerciseDemoColors {
            val w = IntervalTheme.workout
            val dark = w.onGround.luminance() > MID_LUMINANCE
            return ExerciseDemoColors(
                body = w.onGround,
                far = w.onGroundDim,
                joint = w.ground,
                reference = w.control,
                bell = w.work,
                shadow = Color.Black.copy(alpha = if (dark) DARK_SHADOW_ALPHA else LIGHT_SHADOW_ALPHA),
                set = w.prep,
                drive = w.work,
                ease = w.rest,
            )
        }
    }
}

/**
 * [rep] rendered on the unified kinematic & organic-silhouette canvas.
 *
 * Runs by itself unless [progress] holds it or [mode] is [DemoDisplayMode.Thumb]. When [celebrating] changes to
 * a non-null key (e.g. a record set's id) on a standing rep, plays a one-shot [Celebration] gesture then resumes;
 * as the gesture tops out a shockwave sets out behind the figure from the bell it raises ([Celebration.shockAt]).
 */
@Composable
fun ExerciseDemoCanvas(
    rep: ExerciseRep,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    colors: ExerciseDemoColors = ExerciseDemoColors.theme(),
    contentDescription: String? = null,
    onPhase: ((RepPhase?) -> Unit)? = null,
    onSpan: ((RepSpan?) -> Unit)? = null,
    position: RepPosition = rememberRepPosition(rep),
    speed: Float = 1f,
    mode: DemoDisplayMode = DemoDisplayMode.Detail,
    celebrating: Any? = null,
    tracksView: Boolean = true,
) {
    val staticProgress = if (mode == DemoDisplayMode.Thumb) rep.still else progress
    val held by rememberUpdatedState(staticProgress?.coerceIn(rep.earliest, 1f))
    var inView by remember { mutableStateOf(true) }
    val celebration = rememberCelebrationInterlude(rep, position, held, mode, celebrating)
    val activeRep = celebration.rep ?: rep
    val celebratingNow = celebration.rep != null
    RunLoop(rep, position, if (celebratingNow) position.share else staticProgress, inView || !tracksView, speed)
    ReportProgress(rep, position, held, onPhase, onSpan)
    val celebrationState = celebration.rep?.celebration?.let {
        stringResource(if (it == Celebration.RaisedBell) Res.string.rep_raising_bell else Res.string.rep_bowing)
    }
    val semanticsModifier = modifier.canvasSemantics(contentDescription, celebrationState, tracksView) {
        inView = it
    }
    // Compiled as a gesture starts and let go as it ends: a figure in a list of them makes none.
    val shockwave = remember(celebratingNow) { if (celebratingNow) shaderProgram(SHOCKWAVE_SKSL) else null }
    Spacer(
        semanticsModifier.drawWithCache {
            if (size.minDimension <= 0f) return@drawWithCache onDrawBehind {}
            val stage = DemoStage(activeRep, size, mode, colors.reference, this)
            val faintBell = colors.bell.copy(alpha = PATH_ALPHA)
            val shockReach = size.minDimension * SHOCK_REACH
            val shockStroke = Sizes.rowShockwaveStroke.toPx()
            val hot = colors.joint.luminance() < MID_LUMINANCE
            onDrawBehind {
                val at = if (celebratingNow) celebration.progress.value else (held ?: position.share)
                stage.frameFor(at)
                val beatColor = resolveBeatColor(activeRep, mode, at, colors)
                drawWithOptionalClip(stage.clipRect) {
                    val joints = stage.jointsAt(at)
                    if (celebratingNow) {
                        // Out from what the figure raises, or from the middle of one that bows, behind it.
                        val origin = stage.place(joints.celebrated(activeRep.hold))
                        val spent = Celebration.shockAt(at)
                        drawShockwave(shockwave, spent, beatColor, origin, shockReach, shockStroke, hot)
                    }
                    drawCanvasScene(activeRep, stage, joints, at, beatColor, faintBell, colors, mode)
                }
            }
        },
    )
}

/** Where a celebration's shockwave sets out from: what the figure raises, or the hips of one that bows. */
private fun BodyFrame.celebrated(hold: RepHold): Offset = if (hold == RepHold.Free) hips else kettlebellCenter

private class CelebrationInterlude(val rep: ExerciseRep?, val progress: Animatable<Float, *>)

@Composable
private fun rememberCelebrationInterlude(
    rep: ExerciseRep,
    position: RepPosition,
    held: Float?,
    mode: DemoDisplayMode,
    celebrating: Any?,
): CelebrationInterlude {
    val key by rememberUpdatedState(celebrating)
    val stillMotion = prefersReducedMotion()
    var interlude by remember { mutableStateOf<ExerciseRep?>(null) }
    val run = remember { Animatable(0f) }
    LaunchedEffect(rep, position, stillMotion, mode) {
        if (stillMotion || mode == DemoDisplayMode.Thumb || mode == DemoDisplayMode.Ghost) return@LaunchedEffect
        snapshotFlow { key }.filterNotNull().collect {
            val gesture = rep.celebrating(held ?: position.share) ?: return@collect
            interlude = gesture
            try {
                run.snapTo(0f)
                run.animateTo(1f, tween(Celebration.MILLIS, easing = LinearEasing))
            } finally {
                interlude = null
            }
        }
    }
    return CelebrationInterlude(interlude, run)
}

@Composable
private fun ReportProgress(
    rep: ExerciseRep,
    position: RepPosition,
    held: Float?,
    onPhase: ((RepPhase?) -> Unit)?,
    onSpan: ((RepSpan?) -> Unit)?,
) {
    if (onPhase != null) {
        val report by rememberUpdatedState(onPhase)
        LaunchedEffect(rep, position) {
            snapshotFlow { rep.phaseAt(held ?: position.share) }.collect { report(it) }
        }
    }
    if (onSpan != null) {
        val report by rememberUpdatedState(onSpan)
        LaunchedEffect(rep, position) {
            snapshotFlow { rep.spanAt(held ?: position.share) }.collect { report(it) }
        }
    }
}

private fun Modifier.canvasSemantics(
    contentDescription: String?,
    state: String?,
    tracksView: Boolean,
    onVisible: (Boolean) -> Unit,
): Modifier {
    val described = if (contentDescription == null && state == null) {
        this
    } else {
        semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
            if (state != null) this.stateDescription = state
        }
    }
    return if (tracksView) described.onVisibilityChanged(minFractionVisible = 0f, callback = onVisible) else described
}

private fun resolveBeatColor(
    rep: ExerciseRep,
    mode: DemoDisplayMode,
    at: Float,
    colors: ExerciseDemoColors,
): Color = if (mode == DemoDisplayMode.Detail && rep.phases.isNotEmpty() &&
    rep.spans.all { it.beat == RepBeat.Drive }
) {
    colors.bell
} else {
    colors.at(rep, at)
}

private inline fun DrawScope.drawWithOptionalClip(
    clip: androidx.compose.ui.geometry.Rect?,
    block: DrawScope.() -> Unit,
) {
    if (clip != null) clipRect(clip.left, clip.top, clip.right, clip.bottom) { block() } else block()
}

/**
 * How far through its rep a diagram has run by itself: [share], from [ExerciseRep.earliest] to 1. Read while
 * drawing so a running rep redraws without recomposing.
 */
@Stable
class RepPosition internal constructor(start: Float) {
    var share: Float by mutableFloatStateOf(start)
        internal set
}

/** A [RepPosition] for [rep], starting where the rep stands still (or set-up when running). */
@Composable
fun rememberRepPosition(rep: ExerciseRep): RepPosition = remember(rep) { RepPosition(rep.still) }

@Composable
private fun RunLoop(rep: ExerciseRep, position: RepPosition, held: Float?, inView: Boolean, speed: Float) {
    val still = prefersReducedMotion()
    LaunchedEffect(rep, position, held, still, inView, speed) {
        if (held != null) position.share = held.coerceIn(rep.earliest, 1f)
        if (held != null || still || !inView) return@LaunchedEffect
        val from = position.share
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) {
            val elapsed = withInfiniteAnimationFrameMillis { it } - start
            position.share = rep.after(from, elapsed * speed / MILLIS_PER_SECOND)
        }
    }
}

private const val MILLIS_PER_SECOND = 1000f
private const val PATH_ALPHA = 0.4f

/** A celebration's shockwave reaches this share of the canvas's shorter side: about the figure's own height. */
private const val SHOCK_REACH = 0.7f
private const val BEAT_BLEND_SECONDS = 0.12f
private const val MAX_BEAT_BLEND = 0.04f
private const val MID_LUMINANCE = 0.5f
private const val DARK_SHADOW_ALPHA = 0.55f
private const val LIGHT_SHADOW_ALPHA = 0.22f

private class DemoPhase(val caption: String, val rep: ExerciseRep, val at: Float)

@Composable
private fun DemoPhases(vararg phases: DemoPhase) {
    IntervalTheme {
        val palette = IntervalTheme.palette
        Box(Modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = Sizes.content).padding(Spacing.space16),
                verticalArrangement = Arrangement.spacedBy(Spacing.space12),
            ) {
                phases.forEach { phase ->
                    Column(
                        Modifier.fillMaxWidth().clip(Corners.radius18).background(palette.surface)
                            .padding(Spacing.space12),
                    ) {
                        Text(phase.caption, style = IntervalTheme.type.caption, color = palette.textDim)
                        ExerciseDemoCanvas(
                            phase.rep,
                            Modifier.fillMaxWidth().height(Sizes.figurePage),
                            progress = phase.at,
                        )
                    }
                }
            }
        }
    }
}

@PreviewFormFactors
@Composable
private fun ExerciseDemoBottomHingePreview() {
    DemoPhases(
        DemoPhase("Swing: the hike pass, hips back and shins upright", KettlebellReps.Swing, at = 0f),
        DemoPhase("Goblet squat: the bottom, hips down between the heels", KettlebellReps.GobletSquat, at = 0.4f),
    )
}

@PreviewFormFactors
@Composable
private fun ExerciseDemoRackFloatPreview() {
    DemoPhases(
        DemoPhase("Clean: the rack", KettlebellReps.Clean, at = KettlebellReps.Clean.still),
        DemoPhase("Swing: the float", KettlebellReps.Swing, at = KettlebellReps.Swing.still),
    )
}

@PreviewFormFactors
@Composable
private fun ExerciseDemoLockoutPreview() {
    DemoPhases(
        DemoPhase("Snatch: the lockout", KettlebellReps.Snatch, at = KettlebellReps.Snatch.still),
        DemoPhase("Half-kneeling press: on the way up", KettlebellReps.HalfKneelingPress, at = 0.12f),
    )
}
