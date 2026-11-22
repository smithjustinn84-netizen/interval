package dev.justinsmith.lift.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.justinsmith.interval.ui.Angles
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.DemoDisplayMode
import dev.justinsmith.interval.ui.ExerciseDemoCanvas
import dev.justinsmith.interval.ui.ExerciseDemoColors
import dev.justinsmith.interval.ui.ExerciseRep
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.PhaseColors
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.isLargeText
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.ProgramDay
import dev.justinsmith.lift.ui.LiftSizes
import dev.justinsmith.lift.ui.demo.rep
import dev.justinsmith.lift.ui.iconRes
import org.jetbrains.compose.resources.vectorResource

/**
 * Editorial Track & Chalk plate at the top of Strength's Following card: a dark-pine studio telemetry backdrop
 * with calibration dots, subtle corner brackets, a tapered gold registration ribbon, and the lead lift's sculpted
 * [ExerciseDemoCanvas] silhouette (or a completed gold crest when the program is finished). Collapses under
 * [isLargeText] so large typography keeps vertical room.
 */
@Composable
internal fun ProgramHeroBand(day: ProgramDay?, modifier: Modifier = Modifier) {
    if (isLargeText()) return
    val palette = IntervalTheme.palette
    val dark = palette.background.luminance() < DARK_LUMINANCE
    val surface = palette.surface
    val line = palette.line
    val leadRep = remember(day) { day?.blocks?.firstNotNullOfOrNull { it.exercise.rep } }
    val figureColors = remember { heroFigureColors() }
    Box(
        modifier
            .fillMaxWidth()
            .height(LiftSizes.programHeroHeight)
            .background(PhaseColors.ground)
            .clearAndSetSemantics {},
    ) {
        HeroBackdrop(dark = dark, finished = day == null, modifier = Modifier.fillMaxSize())
        if (leadRep != null) {
            LeadFigure(
                rep = leadRep,
                colors = figureColors,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = Spacing.space16, top = Spacing.space4, bottom = Spacing.space4),
            )
        }
        HeroFrameOverlay(dark = dark, surface = surface, line = line, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun LeadFigure(rep: ExerciseRep, colors: ExerciseDemoColors, modifier: Modifier = Modifier) {
    ExerciseDemoCanvas(
        rep = rep,
        modifier = modifier.width(LiftSizes.programHeroFigureWidth).fillMaxHeight(),
        progress = rep.still,
        colors = colors,
        contentDescription = null,
        mode = DemoDisplayMode.Detail,
        tracksView = false,
    )
}

@Composable
private fun HeroBackdrop(dark: Boolean, finished: Boolean, modifier: Modifier = Modifier) {
    val accent = PhaseColors.done
    val dotColor = PhaseColors.onGround.copy(alpha = GRID_DOT_ALPHA)
    val ringTrack = PhaseColors.upcoming
    Spacer(
        modifier.drawWithCache {
            val step = GRID_STEP_DP.dp.toPx()
            val dotRadius = GRID_DOT_RADIUS_DP.dp.toPx()
            val glowCenter = Offset(size.width * GLOW_X_RATIO, size.height * GLOW_Y_RATIO)
            val glow = Brush.radialGradient(
                colors = listOf(
                    accent.copy(alpha = if (dark) DARK_GLOW_ALPHA else LIGHT_GLOW_ALPHA),
                    Color.Transparent,
                ),
                center = glowCenter,
                radius = size.width * GLOW_RADIUS_RATIO,
            )
            val crestStroke = Stroke(width = CREST_STROKE_DP.dp.toPx(), cap = StrokeCap.Round)
            val crestDiameter = size.height * CREST_DIAMETER_RATIO
            val crestTopLeft = Offset(
                size.width - Spacing.space32.toPx() - crestDiameter,
                (size.height - crestDiameter) / 2f,
            )
            onDrawBehind {
                drawRect(glow)
                drawDotMatrix(step, dotRadius, dotColor)
                if (finished) {
                    drawFinishedCrest(crestTopLeft, crestDiameter, crestStroke, accent, ringTrack)
                }
            }
        },
    )
}

@Composable
private fun HeroFrameOverlay(dark: Boolean, surface: Color, line: Color, modifier: Modifier = Modifier) {
    val accent = PhaseColors.done
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
                        DARK_FADE_MID to surface.copy(alpha = DARK_FADE_MID_ALPHA),
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
                    RIBBON_FADE to accent.copy(alpha = RIBBON_ALPHA),
                    1f - RIBBON_FADE to accent.copy(alpha = RIBBON_ALPHA),
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

private fun DrawScope.drawDotMatrix(step: Float, radius: Float, color: Color) {
    var y = step
    while (y < size.height) {
        var x = step
        while (x < size.width) {
            drawCircle(color, radius = radius, center = Offset(x, y))
            x += step
        }
        y += step
    }
}

private fun DrawScope.drawFinishedCrest(
    topLeft: Offset,
    diameter: Float,
    stroke: Stroke,
    accent: Color,
    track: Color,
) {
    val arcSize = Size(diameter, diameter)
    drawArc(track, 0f, Angles.FULL_TURN, false, topLeft, arcSize, style = stroke)
    drawArc(accent, Angles.TWELVE_O_CLOCK, Angles.FULL_TURN, false, topLeft, arcSize, style = stroke)
}

/** Compact rounded tile carrying an [exercise]'s movement or equipment glyph; draws nothing when it has none. */
@Composable
internal fun LiftIconTile(exercise: Exercise, doubleBell: Boolean, modifier: Modifier = Modifier) {
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

private fun heroFigureColors(): ExerciseDemoColors = ExerciseDemoColors(
    body = PhaseColors.onGround,
    far = PhaseColors.onGroundDim,
    joint = PhaseColors.ground,
    reference = PhaseColors.onGroundDim.copy(alpha = REFERENCE_ALPHA),
    bell = PhaseColors.done,
    shadow = PhaseColors.ink.copy(alpha = SHADOW_ALPHA),
    set = PhaseColors.done,
    drive = PhaseColors.done,
    ease = PhaseColors.rest,
)

private const val DARK_LUMINANCE = 0.5f
private const val GRID_STEP_DP = 16f
private const val GRID_DOT_RADIUS_DP = 0.7f
private const val GRID_DOT_ALPHA = 0.14f
private const val GLOW_X_RATIO = 0.78f
private const val GLOW_Y_RATIO = 0.45f
private const val GLOW_RADIUS_RATIO = 0.52f
private const val DARK_GLOW_ALPHA = 0.18f
private const val LIGHT_GLOW_ALPHA = 0.22f
private const val CREST_STROKE_DP = 5f
private const val CREST_DIAMETER_RATIO = 0.56f
private const val CORNER_TICK_ALPHA = 0.24f
private const val RIBBON_FADE = 0.12f
private const val RIBBON_ALPHA = 0.85f
private const val DARK_FADE_START = 0.54f
private const val DARK_FADE_MID = 0.84f
private const val DARK_FADE_MID_ALPHA = 0.62f
private const val LIGHT_EDGE_ALPHA = 0.16f
private const val LIGHT_EDGE_END = 0.26f
private const val LIGHT_FOOT_START = 0.76f
private const val LIGHT_FOOT_ALPHA = 0.28f
private const val REFERENCE_ALPHA = 0.35f
private const val SHADOW_ALPHA = 0.45f
