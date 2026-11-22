package dev.justinsmith.interval.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import dev.justinsmith.interval.platform.LiveHeartRateState
import dev.justinsmith.interval.platform.prefersReducedMotion
import dev.justinsmith.interval.timer.formatClock
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil

/**
 * The rounds a ring's outer lane is drawn as, a segment each: [count] of them, the first [done] finished, and
 * [active] (counted from 1) the one under way, or null between rounds or once over.
 */
class RingLaps(val count: Int, val done: Int, val active: Int?) {
    override fun equals(other: Any?): Boolean =
        other is RingLaps && other.count == count && other.done == done && other.active == active

    override fun hashCode(): Int = (count * 31 + done) * 31 + (active ?: 0)
}

/**
 * A ring filling clockwise from 12 o'clock to [progress] (0 to 1) in [accent], over a track in the
 * workout's control colour: the interval going by, or an EMOM's minute. It [glide]s to each new value
 * over [glideMillis] (the time between the ticks that drive it) so it turns rather than steps; a
 * change past [Motion.RING_SNAP_SHARE] of the ring is a new interval, which snaps. Its stroke is
 * [Sizes.ring], swelling to [Motion.COUNTDOWN_RING_SCALE] of it with [pulse] (0 to 1: the
 * [rememberCountdownPulse] of an interval's last seconds), which the arc flashes to as well.
 *
 * Each value is a provider, read where it is drawn (and [progress] by the one glide that follows it), so a
 * tick, a swell or a crossfade redraws the ring without recomposing it or its caller. Without [glide] the
 * ring draws [progress] as it stands, for a caller that animates it itself.
 *
 * Round it runs a lane: with [laps] a segment a round, finished ones in the workout's on-ground colour, the one
 * under way in [accent], the rest dim, so the rounds sit on the dial rather than in a row under it; without, a
 * dashed hairline.
 *
 * The band is a shader's ([RING_SKSL]) wherever the platform has one: the arc graded along its length to a
 * needle at its head, and with a [dial] ticks cut across it, a second or a rounder step each, and the heat of
 * the interval's last seconds, when the arc brightens and beats (not under reduced motion, which keeps the
 * heat alone). Where there is none (Android before 13) it is the plain arc over its track.
 *
 * With a [zone] (a [rememberRingZone] of the live heart rate) the ring has a bezel too, a hairline in the zone's
 * colour just outside the band, and with a [dial] its ticks are part of it: those on the track etched in the
 * zone's colour, and each carried on outside the band toward the lane, bolder and longer the higher the zone. So
 * the arc says the time left and the marks round it how hard the heart is working. Without the shader the bezel
 * is the hairline alone.
 */
@Composable
fun ProgressRing(
    progress: () -> Float,
    accent: () -> Color,
    modifier: Modifier = Modifier,
    glide: Boolean = true,
    glideMillis: Int = Motion.RING_GLIDE_MILLIS,
    pulse: () -> Float = { 0f },
    laps: (() -> RingLaps?)? = null,
    dial: RingDial? = null,
    zone: RingZone? = null,
) {
    val latest by rememberUpdatedState(progress)
    val shown = remember { Animatable(progress()) }
    if (glide) {
        LaunchedEffect(glideMillis) {
            snapshotFlow { latest() }.collectLatest { target ->
                if (abs(target - shown.value) > Motion.RING_SNAP_SHARE) {
                    shown.snapTo(target)
                } else {
                    shown.animateTo(target, tween(glideMillis, easing = LinearEasing))
                }
            }
        }
    }
    val workout = IntervalTheme.workout
    val colors = RingColors(track = workout.control, lane = workout.upcoming, onGround = workout.onGround)
    val program = remember { shaderProgram(RING_SKSL) }
    val ticks = remember(dial?.seconds) { dial?.let { dialTicks(it.seconds) } }
    val beats = !prefersReducedMotion()
    Spacer(
        modifier.clearAndSetSemantics {}.drawWithCache {
            val strokes = RingStrokes(
                outerGap = Sizes.ringOuterGap.toPx(),
                dashed = Stroke(
                    width = Sizes.hairline.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(Sizes.ringDash.toPx(), Sizes.ringDashGap.toPx()),
                    ),
                ),
                lap = Stroke(width = Sizes.ringLap.toPx()),
                lapGap = Sizes.ringLapGap.toPx(),
                bezel = Stroke(width = Sizes.hairline.toPx()),
            )
            val shader = program?.let { RingShaderPaint(it, Sizes.hairline.toPx()) }
            shader?.dial(ticks)
            onDrawBehind {
                val beat = pulse()
                val width = Sizes.ring.toPx() * (1f + beat * (Motion.COUNTDOWN_RING_SCALE - 1f))
                val sweep = (if (glide) shown.value else latest()).coerceIn(0f, 1f)
                val ink = accent()
                val diameter = drawLane(laps?.invoke()?.takeIf { it.count > 0 }, width, ink, strokes, colors)
                if (shader != null) {
                    val left = dial?.secondsLeft(sweep) ?: 0f
                    val urgent = dial?.urgentSeconds ?: 0f
                    val heat = heatAt(left, urgent)
                    val soft = if (beats) beatAt(left, urgent) * (SOFT_BEAT + (1f - SOFT_BEAT) * heat) else 0f
                    shader.urgency(heat, maxOf(beat, soft))
                    shader.colors(ink, colors.track, etch = colors.onGround, mark = workout.onFill(ink))
                    shader.zone(zone, reach = strokes.outerGap)
                    with(shader) { drawBand(center, diameter / 2, width, sweep) }
                } else {
                    strokes.widen(width)
                    drawBand(sweep, ink, diameter, strokes, colors.track)
                    drawBezel(zone, diameter + width + strokes.outerGap, strokes)
                }
            }
        },
    )
}

/** The beat of an urgent second before the countdown's is this strong as it starts, and full by the end. */
private const val SOFT_BEAT = 0.4f

/** The workout's colours a ring is drawn in: its [track], its outer [lane], and what reads on its ground. */
private class RingColors(val track: Color, val lane: Color, val onGround: Color)

/**
 * The ring's strokes: the outer lane's (dashed, or a [lap] each with [lapGap] between) [outerGap] out from the
 * ring, the [bezel]'s hairline between them, and the two of a ring drawn without its shader, made again only
 * when the width changes (a swell), not on every frame.
 */
private class RingStrokes(
    val outerGap: Float,
    val dashed: Stroke,
    val lap: Stroke,
    val lapGap: Float,
    val bezel: Stroke,
) {
    var track = Stroke(0f)
        private set
    var arc = Stroke(0f, cap = StrokeCap.Round)
        private set

    fun widen(width: Float) {
        if (width == track.width) return
        track = Stroke(width)
        arc = Stroke(width, cap = StrokeCap.Round)
    }
}

/**
 * The ring's outer lane: [laps] a segment a round, or with none a dashed hairline. Gives back the diameter of
 * the circle the band, [width] wide, runs round inside it.
 */
private fun DrawScope.drawLane(
    laps: RingLaps?,
    width: Float,
    accent: Color,
    strokes: RingStrokes,
    colors: RingColors,
): Float {
    val outerWidth = if (laps != null) strokes.lap.width else strokes.dashed.width
    val outerDiameter = size.minDimension - outerWidth
    if (outerDiameter > 0f) {
        if (laps != null) {
            drawLaps(laps, outerDiameter, strokes.lap, strokes.lapGap, accent, colors.onGround, colors.lane)
        } else {
            drawDashedLane(outerDiameter, strokes.dashed, colors.lane)
        }
    }
    val lane = if (laps != null) outerWidth else 0f
    return size.minDimension - width - (strokes.outerGap + lane) * 2
}

/** The band without its shader: a circle [diameter] across filled to [sweep] in [accent] over its [track]. */
private fun DrawScope.drawBand(sweep: Float, accent: Color, diameter: Float, strokes: RingStrokes, track: Color) {
    val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
    val arc = Size(diameter, diameter)
    drawArc(track, 0f, Angles.FULL_TURN, useCenter = false, topLeft, arc, style = strokes.track)
    if (sweep > 0f) {
        drawArc(accent, Angles.TWELVE_O_CLOCK, Angles.FULL_TURN * sweep, false, topLeft, arc, style = strokes.arc)
    }
}

/**
 * The bezel without its shader: a hairline round a circle [diameter] across, midway between the band and its
 * lane, in the [zone]'s colour and as strong as the zone is.
 */
private fun DrawScope.drawBezel(zone: RingZone?, diameter: Float, strokes: RingStrokes) {
    val strength = zone?.strength ?: 0f
    if (zone == null || strength <= 0f) return
    val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
    val color = zone.color.copy(alpha = strength)
    drawArc(color, 0f, Angles.FULL_TURN, useCenter = false, topLeft, Size(diameter, diameter), style = strokes.bezel)
}

/** The outer lane with no laps: a dashed hairline round a circle [diameter] across, centred. */
private fun DrawScope.drawDashedLane(diameter: Float, stroke: Stroke, color: Color) {
    val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
    drawArc(color, 0f, Angles.FULL_TURN, useCenter = false, topLeft, Size(diameter, diameter), style = stroke)
}

/**
 * [lap]'s rounds round a circle [diameter] across, centred: a segment each with [gapPx] between, the finished ones
 * in [done], the one under way in [accent], the rest in [upcoming].
 */
private fun DrawScope.drawLaps(
    lap: RingLaps,
    diameter: Float,
    stroke: Stroke,
    gapPx: Float,
    accent: Color,
    done: Color,
    upcoming: Color,
) {
    val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
    val each = Angles.FULL_TURN / lap.count
    val gap = (gapPx / (PI.toFloat() * diameter) * Angles.FULL_TURN).coerceAtMost(each / 2)
    repeat(lap.count) { index ->
        val round = index + 1
        val color = when {
            round == lap.active -> accent
            round <= lap.done -> done
            else -> upcoming
        }
        val start = Angles.TWELVE_O_CLOCK + each * index + gap / 2
        drawArc(color, start, each - gap, useCenter = false, topLeft, Size(diameter, diameter), style = stroke)
    }
}

/**
 * The ring as a dial in each of its states, on the workout's ground: under way with its laps, a minute filling,
 * a full turn, and an interval's last seconds, its arc hot and its clock part filled.
 */
@OptIn(ExperimentalLayoutApi::class)
@Preview
@Composable
private fun ProgressRingPreview() {
    // The ring is drawn in the workout's colours, read from the theme: dark, as the workout runs.
    IntervalTheme(darkTheme = true) {
        val workout = IntervalTheme.workout
        val rings = listOf(
            PreviewRing(progress = 0.62f, RingDial(seconds = 30, draining = true), workout.work, RingLaps(8, 3, 4)),
            PreviewRing(progress = 0.25f, RingDial(seconds = 60), workout.rest),
            PreviewRing(progress = 1f, dial = null, workout.done),
            PreviewRing(progress = 0.18f, RingDial(seconds = 30, draining = true), workout.work, RingLaps(8, 3, 4)),
        )
        FlowRow(
            Modifier.fillMaxSize().background(workout.ground).padding(Spacing.space16),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space16),
            verticalArrangement = Arrangement.spacedBy(Spacing.space16),
        ) {
            for (ring in rings) {
                Box(Modifier.size(Sizes.figurePage), contentAlignment = Alignment.Center) {
                    ProgressRing(
                        { ring.progress },
                        { ring.accent },
                        Modifier.fillMaxSize(),
                        glide = false,
                        laps = { ring.laps },
                        dial = ring.dial,
                    )
                    val left = ceil(ring.dial?.secondsLeft(ring.progress) ?: 0f).toInt()
                    CountdownClock(
                        formatClock(left),
                        intervalKey = Unit,
                        countingDown = false,
                        accent = ring.accent,
                        style = IntervalTheme.type.display,
                        heat = { ring.dial?.heat(ring.progress) ?: 0f },
                    )
                }
            }
        }
    }
}

private class PreviewRing(val progress: Float, val dial: RingDial?, val accent: Color, val laps: RingLaps? = null)

/**
 * The ring as a bezel of the live heart rate, a ring a zone from the easiest to the hardest: the ticks on the
 * track and the marks outside the band in the zone's colour, there too with the arc still full.
 */
@OptIn(ExperimentalLayoutApi::class)
@Preview
@Composable
private fun ProgressRingZonesPreview() {
    IntervalTheme(darkTheme = true) {
        val workout = IntervalTheme.workout
        val dial = RingDial(seconds = 30, draining = true)
        val rings = listOf(
            PreviewZone(bpm = 100, progress = 0.2f, workout.rest),
            PreviewZone(bpm = 125, progress = 0.9f, workout.work),
            PreviewZone(bpm = 145, progress = 0.6f, workout.work),
            PreviewZone(bpm = 162, progress = 0.35f, workout.work),
            PreviewZone(bpm = 182, progress = 1f, workout.work),
        )
        FlowRow(
            Modifier.fillMaxSize().background(workout.ground).padding(Spacing.space16),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space16),
            verticalArrangement = Arrangement.spacedBy(Spacing.space16),
        ) {
            for (ring in rings) {
                Box(Modifier.size(Sizes.figurePage), contentAlignment = Alignment.Center) {
                    ProgressRing(
                        { ring.progress },
                        { ring.accent },
                        Modifier.fillMaxSize(),
                        glide = false,
                        laps = { RingLaps(count = 8, done = 3, active = 4) },
                        dial = dial,
                        zone = rememberRingZone(LiveHeartRateState.Reading(ring.bpm)),
                    )
                    CountdownClock(
                        formatClock(ceil(dial.secondsLeft(ring.progress)).toInt()),
                        intervalKey = Unit,
                        countingDown = false,
                        accent = ring.accent,
                        style = IntervalTheme.type.display,
                    )
                }
            }
        }
    }
}

private class PreviewZone(val bpm: Int, val progress: Float, val accent: Color)
