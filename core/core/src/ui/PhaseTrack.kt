package dev.justinsmith.interval.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * How far through a rep something is, 0 to 1 (or negative while setting up), asked for while drawing: a lambda
 * that hands back a plain float, so a frame boxes nothing.
 */
fun interface RepShare {
    fun now(): Float
}

/**
 * [rep] laid out as a bar, a stretch for each part of it, the active one lit in its beat's colour, with a marker
 * where [progress] says it is. Tapping or dragging along it calls [onScrub] with the place, 0 to 1 through the rep;
 * a screen reader moves it as a slider, and the arrow keys step it from one stretch to the next.
 *
 * Where two stretches meet is a checkpoint ([ExerciseRep.checkpoints]: the hike, the rack, the lockout), a bead
 * on the bar. A drag has a detent at each: within [Sizes.demoDetent] of one the marker clicks onto it, so the
 * figure stands in exactly that stance, and as it comes to one or passes it the bead swells and lights, a ring
 * of the colour of the stretch that arrives there goes out from it, and a tick lands under the thumb. A tap goes
 * where it lands, with none of that.
 */
@Composable
fun PhaseTrack(
    rep: ExerciseRep,
    progress: RepShare,
    onScrub: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    colors: ExerciseDemoColors = ExerciseDemoColors.theme(),
) {
    val palette = IntervalTheme.palette
    val scrub by rememberUpdatedState(onScrub)
    val at by rememberUpdatedState(progress)
    val detent = remember(rep) { DetentGlow() }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(Sizes.iconButton)
            .pointerInput(rep) { detectTapGestures { scrub(placeAt(it.x, size.width)) } }
            .scrubbedWithDetents(rep, detent) { scrub(it) }
            .steppedByArrows(rep, { at.now() }) { scrub(it) }
            .focusable()
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(at.now().coerceIn(0f, 1f), 0f..1f)
                setProgress { to ->
                    scrub(to.coerceIn(0f, 1f))
                    true
                }
            },
    ) {
        val raw = at.now()
        val settingUp = raw < 0f && rep.setUp != null
        val now = raw.coerceIn(0f, 1f)
        drawStretches(rep, now, settingUp, colors, palette.line)
        drawCheckpoints(rep, if (settingUp) Float.NaN else now, detent, colors, palette)
        val markerWidth = Sizes.demoMarkerWidth.toPx()
        val markerHeight = Sizes.demoMarkerHeight.toPx()
        val x = (now * size.width).coerceIn(markerWidth / 2, size.width - markerWidth / 2)
        drawRoundRect(
            palette.text,
            topLeft = Offset(x - markerWidth / 2, size.height / 2 - markerHeight / 2),
            size = Size(markerWidth, markerHeight),
            cornerRadius = CornerRadius(markerWidth / 2),
        )
    }
}

/**
 * A drag along the track scrubs through [rep], with a detent at each checkpoint: the place clicks onto one within
 * [Sizes.demoDetent], and coming to one ticks under the thumb and lights it in [detent].
 */
@Composable
private fun Modifier.scrubbedWithDetents(rep: ExerciseRep, detent: DetentGlow, onScrub: (Float) -> Unit): Modifier {
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    val scrub by rememberUpdatedState(onScrub)
    val scope = rememberCoroutineScope()
    return pointerInput(rep) {
        val checkpoints = rep.checkpoints
        val reach = Sizes.demoDetent.toPx()
        var from = Float.NaN
        val dragTo = { x: Float ->
            val width = size.width
            val place = checkpoints.clickedOnto(placeAt(x, width), if (width <= 0) 0f else reach / width)
            val reached = checkpoints.reached(from, place)
            from = place
            if (reached >= 0) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch { detent.light(reached) }
            }
            scrub(place)
        }
        detectHorizontalDragGestures(
            onDragStart = {
                from = Float.NaN
                dragTo(it.x)
            },
        ) { change, _ ->
            change.consume()
            dragTo(change.position.x)
        }
    }
}

/** The arrow keys step [rep] from one stretch to the next, from where [at] says it is. */
private fun Modifier.steppedByArrows(rep: ExerciseRep, at: () -> Float, onScrub: (Float) -> Unit): Modifier =
    onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (event.key) {
            Key.DirectionRight -> onScrub(rep.stepAfter(at()))
            Key.DirectionLeft -> onScrub(rep.stepBefore(at()))
            else -> return@onKeyEvent false
        }
        true
    }

/** The bar: a stretch for each part of [rep], the one [now] is in lit in its beat's colour unless [settingUp]. */
private fun DrawScope.drawStretches(
    rep: ExerciseRep,
    now: Float,
    settingUp: Boolean,
    colors: ExerciseDemoColors,
    unlit: Color,
) {
    val track = Sizes.progressBar.toPx()
    val gap = Sizes.demoStretchGap.toPx()
    val middle = size.height / 2
    val spans = rep.spans
    for (index in spans.indices) {
        val span = spans[index]
        val lit = !settingUp && now <= span.until && (now > span.from || (now == 0f && span.from == 0f))
        drawRoundRect(
            if (lit) colors.of(span.beat) else unlit,
            topLeft = Offset(span.from * size.width + gap / 2, middle - track / 2),
            size = Size(((span.until - span.from) * size.width - gap).coerceAtLeast(0f), track),
            cornerRadius = CornerRadius(track / 2),
        )
    }
}

/**
 * A bead at each of [rep]'s checkpoints: swollen and bright while the marker stands on it ([now], not a number
 * while setting up) or as [detent] lights it, when a ring of the colour of the stretch that arrives there goes out
 * from it and is gone: the detent, seen.
 */
private fun DrawScope.drawCheckpoints(
    rep: ExerciseRep,
    now: Float,
    detent: DetentGlow,
    colors: ExerciseDemoColors,
    palette: IntervalPalette,
) {
    val bead = Sizes.demoNode.toPx() / 2
    val checkpoints = rep.checkpoints
    for (index in checkpoints.indices) {
        val at = Offset(checkpoints[index] * size.width, size.height / 2)
        val glow = if (index == detent.index) detent.glow.value else 0f
        val lit = if (abs(now - checkpoints[index]) < PARKED) max(glow, PARKED_GLOW) else glow
        if (glow > 0f) {
            drawCircle(
                colors.of(rep.spans[index].beat),
                radius = bead * (1f + (Motion.DETENT_RING_SCALE - 1f) * (1f - glow)),
                center = at,
                alpha = glow,
                style = Stroke(Sizes.hairline.toPx() * 2),
            )
        }
        drawCircle(
            lerp(palette.textDim, palette.text, lit),
            radius = bead * (1f + (Motion.DETENT_POP_SCALE - 1f) * lit),
            center = at,
        )
    }
}

/** How far through a rep a touch at [x] along a track [width] wide is. */
private fun placeAt(x: Float, width: Int): Float = if (width <= 0) 0f else (x / width).coerceIn(0f, 1f)

/**
 * The checkpoint a drag last came to, and how lit its bead still is: 1 as the marker clicks onto it, back to 0 over
 * [Motion.DETENT_MILLIS]. Read while drawing, so a bead fading redraws the track and recomposes nothing.
 */
@Stable
private class DetentGlow {
    var index by mutableIntStateOf(NO_CHECKPOINT)
        private set
    val glow = Animatable(0f)

    suspend fun light(checkpoint: Int) {
        index = checkpoint
        glow.snapTo(1f)
        glow.animateTo(0f, tween(Motion.DETENT_MILLIS, easing = LinearOutSlowInEasing))
    }
}

/**
 * [place] as a drag's detents leave it: on the checkpoint within [reach] of it, the nearer of two, else where it
 * is.
 */
internal fun List<Float>.clickedOnto(place: Float, reach: Float): Float {
    var nearest = place
    var gap = reach
    for (index in indices) {
        val away = abs(this[index] - place)
        if (away <= gap) {
            gap = away
            nearest = this[index]
        }
    }
    return nearest
}

/**
 * The checkpoint a marker dragged [from] one place [to] another came to, by its index: the last it reached or
 * passed on the way, so a quick drag over two ticks once, for the one nearer where it now is. One it only left, or
 * stays on, is none ([NO_CHECKPOINT]): a thumb resting on a detent ticks once, not at every tremor. With nowhere
 * to have come from ([from] not a number, as a drag starts), the one it stands on.
 */
internal fun List<Float>.reached(from: Float, to: Float): Int {
    if (from.isNaN()) return indexOf(to)
    var reached = NO_CHECKPOINT
    for (index in indices) {
        val checkpoint = this[index]
        when {
            from < checkpoint && checkpoint <= to -> reached = index
            to <= checkpoint && checkpoint < from && reached == NO_CHECKPOINT -> reached = index
        }
    }
    return reached
}

/** No checkpoint. */
internal const val NO_CHECKPOINT = -1

/** How near a checkpoint the marker counts as standing on it, and how lit its bead is while it does. */
private const val PARKED = 0.0005f
private const val PARKED_GLOW = 0.6f
