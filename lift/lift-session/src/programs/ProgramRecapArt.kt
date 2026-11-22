package dev.justinsmith.lift.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.justinsmith.interval.ui.Angles
import dev.justinsmith.interval.ui.Burst
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.PhaseColors
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.isLargeText
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.LiftChange
import dev.justinsmith.lift.domain.NextCycle
import dev.justinsmith.lift.domain.ProgramRecap
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.ui.LiftSizes
import dev.justinsmith.lift.ui.iconRes
import org.jetbrains.compose.resources.vectorResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Celebratory Track & Chalk completion crest at the top of [NextCycleCard]: a dark-pine studio telemetry plate
 * holding the program's completion ring dial (with etched dial ticks and a one-shot [Burst] on strong completion)
 * beside the next-cycle verdict emblem. Collapses under [isLargeText].
 */
@Composable
internal fun RecapHeroBand(recap: ProgramRecap, modifier: Modifier = Modifier) {
    if (isLargeText()) return
    val palette = IntervalTheme.palette
    val dark = palette.background.luminance() < DARK_LUMINANCE
    val tone = if (recap.next is NextCycle.EaseOff) PhaseColors.rest else PhaseColors.done
    val completion = recap.completion.toFloat().coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(LiftSizes.recapHeroHeight)
            .background(PhaseColors.ground)
            .clearAndSetSemantics {},
    ) {
        RecapPlateBackdrop(dark = dark, tone = tone, modifier = Modifier.fillMaxSize())
        if (recap.next !is NextCycle.EaseOff) {
            Burst(
                colors = listOf(tone, PhaseColors.onGround),
                modifier = Modifier.fillMaxSize(),
                stroke = Sizes.rowBurstStroke,
                shock = true,
            )
        }
        Row(
            Modifier.fillMaxSize().padding(horizontal = Spacing.space20),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space20),
        ) {
            CompletionDial(completion = completion, tone = tone)
            Spacer(Modifier.weight(1f))
            VerdictEmblem(next = recap.next, tone = tone)
        }
        RecapPlateFrame(
            dark = dark,
            surface = palette.surface,
            line = palette.line,
            tone = tone,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun CompletionDial(completion: Float, tone: Color, modifier: Modifier = Modifier) {
    val track = PhaseColors.upcoming
    val tickColor = PhaseColors.onGround.copy(alpha = DIAL_TICK_ALPHA)
    Box(modifier.size(LiftSizes.recapDial), contentAlignment = Alignment.Center) {
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val strokePx = LiftSizes.recapDialStroke.toPx()
                val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
                val diameter = (size.minDimension - strokePx).coerceAtLeast(1f)
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                val outerRadius = diameter / 2f + strokePx * TICK_OUTER_OFFSET
                val tickLen = DIAL_TICK_DP.dp.toPx()
                val tickWidth = Sizes.hairline.toPx()
                onDrawBehind {
                    drawArc(track, 0f, Angles.FULL_TURN, false, topLeft, arcSize, style = stroke)
                    if (completion > 0f) {
                        drawArc(
                            tone,
                            Angles.TWELVE_O_CLOCK,
                            Angles.FULL_TURN * completion,
                            false,
                            topLeft,
                            arcSize,
                            style = stroke,
                        )
                    }
                    drawDialTicks(center, outerRadius, tickLen, tickWidth, tickColor)
                }
            },
        )
        Text(
            "${(completion * PERCENT).roundToInt()}%",
            style = IntervalTheme.type.control.copy(
                fontSize = DIAL_LABEL_SP.sp,
                lineHeight = DIAL_LABEL_SP.sp,
                fontFeatureSettings = TABULAR,
            ),
            color = PhaseColors.onGround,
            maxLines = 1,
        )
    }
}

@Composable
private fun VerdictEmblem(next: NextCycle, tone: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(Sizes.emptyIconRing)
            .clip(CircleShape)
            .background(tone.copy(alpha = EMBLEM_FILL_ALPHA))
            .border(Sizes.hairline, tone.copy(alpha = EMBLEM_BORDER_ALPHA), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(verdictIcon(next), contentDescription = null, tint = tone, modifier = Modifier.size(Sizes.emptyIcon))
    }
}

private fun verdictIcon(next: NextCycle): ImageVector = when (next) {
    is NextCycle.MoveOn -> IntervalIcons.Streak
    is NextCycle.GoAgain -> IntervalIcons.Bell
    is NextCycle.EaseOff -> IntervalIcons.Ease
}

@Composable
private fun RecapPlateBackdrop(dark: Boolean, tone: Color, modifier: Modifier = Modifier) {
    val dotColor = PhaseColors.onGround.copy(alpha = GRID_DOT_ALPHA)
    Spacer(
        modifier.drawWithCache {
            val step = GRID_STEP_DP.dp.toPx()
            val dotRadius = GRID_DOT_RADIUS_DP.dp.toPx()
            val glow = Brush.radialGradient(
                colors = listOf(tone.copy(alpha = if (dark) DARK_GLOW_ALPHA else LIGHT_GLOW_ALPHA), Color.Transparent),
                center = Offset(size.width * GLOW_X_RATIO, size.height * GLOW_Y_RATIO),
                radius = size.width * GLOW_RADIUS_RATIO,
            )
            onDrawBehind {
                drawRect(glow)
                var y = step
                while (y < size.height) {
                    var x = step
                    while (x < size.width) {
                        drawCircle(dotColor, radius = dotRadius, center = Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
        },
    )
}

@Composable
private fun RecapPlateFrame(
    dark: Boolean,
    surface: Color,
    line: Color,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier.drawWithCache {
            val stripHeight = LiftSizes.programHeroAccentStrip.toPx()
            val hair = Sizes.hairline.toPx()
            val inset = Spacing.space12.toPx()
            val tick = LiftSizes.programHeroCornerTick.toPx()
            val tickColor = PhaseColors.onGround.copy(alpha = CORNER_TICK_ALPHA)
            val fade = if (dark) {
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        DARK_FADE_START to Color.Transparent,
                        1f to surface,
                    ),
                    startY = 0f,
                    endY = size.height,
                )
            } else {
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to PhaseColors.ground.copy(alpha = LIGHT_EDGE_ALPHA),
                        LIGHT_EDGE_END to Color.Transparent,
                        LIGHT_FOOT_START to Color.Transparent,
                        1f to PhaseColors.ground.copy(alpha = LIGHT_FOOT_ALPHA),
                    ),
                    startY = 0f,
                    endY = size.height,
                )
            }
            val ribbon = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    RIBBON_FADE to tone.copy(alpha = RIBBON_ALPHA),
                    1f - RIBBON_FADE to tone.copy(alpha = RIBBON_ALPHA),
                    1f to Color.Transparent,
                ),
                startX = 0f,
                endX = size.width,
            )
            onDrawBehind {
                drawRect(fade)
                drawRect(ribbon, topLeft = Offset.Zero, size = Size(size.width, stripHeight))
                val rightX = size.width - inset
                drawLine(tickColor, Offset(inset, inset), Offset(inset + tick, inset), hair)
                drawLine(tickColor, Offset(inset, inset), Offset(inset, inset + tick), hair)
                drawLine(tickColor, Offset(rightX - tick, inset), Offset(rightX, inset), hair)
                drawLine(tickColor, Offset(rightX, inset), Offset(rightX, inset + tick), hair)
                if (!dark) {
                    val y = size.height - hair / 2f
                    drawLine(line, Offset(0f, y), Offset(size.width, y), hair)
                }
            }
        },
    )
}

/** Compact rounded tile carrying an [exercise]'s movement or equipment glyph; draws nothing when it has none. */
@Composable
internal fun RecapLiftTile(exercise: Exercise, doubleBell: Boolean, modifier: Modifier = Modifier) {
    val icon = exercise.iconRes(doubleBell) ?: return
    val palette = IntervalTheme.palette
    Box(
        modifier
            .size(LiftSizes.liftTile)
            .clip(Corners.radius12)
            .background(palette.surfaceVariant)
            .border(Sizes.hairline, palette.line, Corners.radius12)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            vectorResource(icon),
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.size(Sizes.iconInline),
        )
    }
}

/** Normalized start-to-end positions along a lift progression track, with whether the lift grew. */
internal data class LiftChangeSpan(val start: Float, val end: Float, val gained: Boolean)

/** Computes the visual [LiftChangeSpan] comparing a lift's [first] best working set against its [last]. */
internal fun liftChangeSpan(first: SetLog, last: SetLog): LiftChangeSpan {
    val a = setMagnitude(first)
    val b = setMagnitude(last)
    return when {
        b > a -> {
            val start = ((a / b) * SPAN_MAX).toFloat().coerceIn(SPAN_MIN, SPAN_GAIN_START_CAP)
            LiftChangeSpan(start = start, end = SPAN_MAX, gained = true)
        }

        b < a -> {
            val end = ((b / a) * SPAN_MAX).toFloat().coerceIn(SPAN_MIN, SPAN_GAIN_START_CAP)
            LiftChangeSpan(start = SPAN_MAX, end = end, gained = false)
        }

        else -> LiftChangeSpan(start = SPAN_STEADY_START, end = SPAN_STEADY_END, gained = false)
    }
}

private fun setMagnitude(set: SetLog): Double {
    val kg = set.load.kg
    val hold = set.seconds ?: 0
    val count = when {
        set.reps > 0 -> set.reps.toDouble()
        hold > 0 -> hold.toDouble()
        else -> 1.0
    }
    return if (kg > 0.0) kg * count else count
}

/** Two-point visual comparison track illustrating a [LiftChange] from starting set to finishing set. */
@Composable
internal fun LiftChangeTrack(lift: LiftChange, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val span = liftChangeSpan(lift.first, lift.last)
    val railColor = palette.line
    val startColor = palette.textDim
    val beamColor = if (span.gained) palette.accent else palette.restMark
    Spacer(
        modifier
            .fillMaxWidth()
            .height(LiftSizes.recapTrackHeight)
            .clearAndSetSemantics {}
            .drawWithCache {
                val trackStroke = Sizes.hairline.toPx()
                val beamStroke = LiftSizes.recapTrackStroke.toPx()
                val nodeRadius = LiftSizes.recapTrackNode.toPx()
                val pad = nodeRadius + trackStroke
                val usable = (size.width - pad * 2f).coerceAtLeast(1f)
                val cy = size.height / 2f
                val startX = pad + usable * span.start
                val endX = pad + usable * span.end
                onDrawBehind {
                    drawLine(railColor, Offset(pad, cy), Offset(size.width - pad, cy), trackStroke, StrokeCap.Round)
                    drawLine(beamColor, Offset(startX, cy), Offset(endX, cy), beamStroke, StrokeCap.Round)
                    drawCircle(startColor, radius = nodeRadius * START_NODE_SCALE, center = Offset(startX, cy))
                    drawCircle(beamColor, radius = nodeRadius, center = Offset(endX, cy))
                }
            },
    )
}

private fun DrawScope.drawDialTicks(
    center: Offset,
    radius: Float,
    length: Float,
    stroke: Float,
    color: Color,
) {
    repeat(DIAL_TICK_COUNT) { i ->
        val angle = (i * Angles.FULL_TURN / DIAL_TICK_COUNT) * (PI / Angles.HALF_TURN)
        val cosA = cos(angle).toFloat()
        val sinA = sin(angle).toFloat()
        val inner = radius - length / 2f
        val outer = radius + length / 2f
        drawLine(
            color,
            Offset(center.x + inner * cosA, center.y + inner * sinA),
            Offset(center.x + outer * cosA, center.y + outer * sinA),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private const val DARK_LUMINANCE = 0.5f
private const val PERCENT = 100
private const val DIAL_LABEL_SP = 20f
private const val TABULAR = "tnum"
private const val DIAL_TICK_COUNT = 12
private const val DIAL_TICK_DP = 4f
private const val DIAL_TICK_ALPHA = 0.28f
private const val TICK_OUTER_OFFSET = 0.55f
private const val EMBLEM_FILL_ALPHA = 0.16f
private const val EMBLEM_BORDER_ALPHA = 0.42f
private const val GRID_STEP_DP = 16f
private const val GRID_DOT_RADIUS_DP = 0.7f
private const val GRID_DOT_ALPHA = 0.14f
private const val GLOW_X_RATIO = 0.25f
private const val GLOW_Y_RATIO = 0.5f
private const val GLOW_RADIUS_RATIO = 0.65f
private const val DARK_GLOW_ALPHA = 0.18f
private const val LIGHT_GLOW_ALPHA = 0.24f
private const val CORNER_TICK_ALPHA = 0.24f
private const val RIBBON_FADE = 0.12f
private const val RIBBON_ALPHA = 0.85f
private const val DARK_FADE_START = 0.72f
private const val LIGHT_EDGE_ALPHA = 0.16f
private const val LIGHT_EDGE_END = 0.24f
private const val LIGHT_FOOT_START = 0.78f
private const val LIGHT_FOOT_ALPHA = 0.26f
private const val SPAN_MIN = 0.16f
private const val SPAN_MAX = 0.94f
private const val SPAN_GAIN_START_CAP = 0.72f
private const val SPAN_STEADY_START = 0.26f
private const val SPAN_STEADY_END = 0.74f
private const val START_NODE_SCALE = 0.75f
