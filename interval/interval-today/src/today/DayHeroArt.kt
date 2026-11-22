package dev.justinsmith.interval.today

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.today.resources.Res
import dev.justinsmith.interval.today.resources.today_art_bjj
import dev.justinsmith.interval.today.resources.today_art_climbing
import dev.justinsmith.interval.today.resources.today_art_conditioning
import dev.justinsmith.interval.today.resources.today_art_done
import dev.justinsmith.interval.today.resources.today_art_empty
import dev.justinsmith.interval.today.resources.today_art_mobility
import dev.justinsmith.interval.today.resources.today_art_rest
import dev.justinsmith.interval.today.resources.today_art_running
import dev.justinsmith.interval.today.resources.today_art_sport
import dev.justinsmith.interval.today.resources.today_art_strength
import dev.justinsmith.interval.ui.IntervalSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.PhaseColors
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.isLargeText
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * The studio editorial header plate shown at the top of Today's primary card, one per training mood or state.
 * Purely decorative: the day's caption and steps carry every word.
 */
internal enum class DayArt {
    Strength,
    Conditioning,
    Mobility,
    Bjj,
    Climbing,
    Running,
    Sport,
    Rest,
    Done,
    Empty,
}

/** Which editorial plate fits this [DayKind]: each sport has its own, with [DayArt.Sport] for custom sports. */
internal val DayKind.art: DayArt
    get() = when (this) {
        DayKind.Strength -> DayArt.Strength

        DayKind.Conditioning -> DayArt.Conditioning

        DayKind.Mobility -> DayArt.Mobility

        DayKind.Rest -> DayArt.Rest

        DayKind.Done -> DayArt.Done

        is DayKind.SportDay -> when (sport) {
            Sport.Bjj -> DayArt.Bjj
            Sport.Climbing -> DayArt.Climbing
            Sport.Running -> DayArt.Running
            is Sport.Custom -> DayArt.Sport
        }
    }

/**
 * Compact studio artwork band at the top of Today's primary card. In dark mode its lower edge dissolves directly
 * into the card surface; in light mode it stands as a framed pine studio plate above the white card body, separated
 * by a hairline. Hidden under [isLargeText] so large typography and the primary Start action keep vertical room.
 */
@Composable
internal fun DayHeroBand(art: DayArt, tone: Color, modifier: Modifier = Modifier) {
    if (isLargeText()) return
    val palette = IntervalTheme.palette
    val dark = palette.background.luminance() < DARK_LUMINANCE
    val surface = palette.surface
    val line = palette.line
    Box(
        modifier
            .fillMaxWidth()
            .height(IntervalSizes.todayArtHeight)
            .background(PhaseColors.ground)
            .clearAndSetSemantics {},
    ) {
        Image(
            painter = painterResource(art.drawable),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd,
            modifier = Modifier.fillMaxSize(),
        )
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val stripHeight = IntervalSizes.todayArtAccentStrip.toPx()
                val hair = Sizes.hairline.toPx()
                val inset = Spacing.space12.toPx()
                val tick = IntervalSizes.todayArtCornerTick.toPx()
                val tickColor = PhaseColors.onGround.copy(alpha = CORNER_TICK_ALPHA)
                val bottomFade = heroScrim(dark, surface, size.height)
                val topRibbon = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        TOP_RIBBON_FADE to tone.copy(alpha = TOP_RIBBON_ALPHA),
                        1f - TOP_RIBBON_FADE to tone.copy(alpha = TOP_RIBBON_ALPHA),
                        1f to Color.Transparent,
                    ),
                    startX = 0f,
                    endX = size.width,
                )
                onDrawBehind {
                    drawRect(bottomFade)
                    drawRect(topRibbon, topLeft = Offset.Zero, size = Size(size.width, stripHeight))
                    drawUpperCornerTicks(inset, tick, hair, tickColor)
                    if (!dark) {
                        val y = size.height - hair / 2f
                        drawLine(line, Offset(0f, y), Offset(size.width, y), hair)
                    }
                }
            },
        )
    }
}

private fun heroScrim(dark: Boolean, surface: Color, height: Float): Brush = if (dark) {
    Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            DARK_FADE_START to Color.Transparent,
            DARK_FADE_MID to surface.copy(alpha = DARK_FADE_MID_ALPHA),
            1f to surface,
        ),
        startY = 0f,
        endY = height,
    )
} else {
    Brush.verticalGradient(
        colorStops = arrayOf(
            0f to PhaseColors.ground.copy(alpha = LIGHT_EDGE_ALPHA),
            LIGHT_VIGNETTE_END to Color.Transparent,
            LIGHT_FOOT_START to Color.Transparent,
            1f to PhaseColors.ground.copy(alpha = LIGHT_FOOT_ALPHA),
        ),
        startY = 0f,
        endY = height,
    )
}

/** Quiet printed corner registration ticks along the upper frame of [DayHeroBand]. */
private fun DrawScope.drawUpperCornerTicks(inset: Float, tick: Float, stroke: Float, color: Color) {
    val rightX = size.width - inset
    drawLine(color, Offset(inset, inset), Offset(inset + tick, inset), stroke)
    drawLine(color, Offset(inset, inset), Offset(inset, inset + tick), stroke)
    drawLine(color, Offset(rightX - tick, inset), Offset(rightX, inset), stroke)
    drawLine(color, Offset(rightX, inset), Offset(rightX, inset + tick), stroke)
}

private val DayArt.drawable: DrawableResource
    get() = when (this) {
        DayArt.Strength -> Res.drawable.today_art_strength
        DayArt.Conditioning -> Res.drawable.today_art_conditioning
        DayArt.Mobility -> Res.drawable.today_art_mobility
        DayArt.Bjj -> Res.drawable.today_art_bjj
        DayArt.Climbing -> Res.drawable.today_art_climbing
        DayArt.Running -> Res.drawable.today_art_running
        DayArt.Sport -> Res.drawable.today_art_sport
        DayArt.Rest -> Res.drawable.today_art_rest
        DayArt.Done -> Res.drawable.today_art_done
        DayArt.Empty -> Res.drawable.today_art_empty
    }

private const val DARK_LUMINANCE = 0.5f
private const val CORNER_TICK_ALPHA = 0.24f
private const val TOP_RIBBON_FADE = 0.12f
private const val TOP_RIBBON_ALPHA = 0.85f
private const val DARK_FADE_START = 0.48f
private const val DARK_FADE_MID = 0.82f
private const val DARK_FADE_MID_ALPHA = 0.65f
private const val LIGHT_EDGE_ALPHA = 0.18f
private const val LIGHT_VIGNETTE_END = 0.28f
private const val LIGHT_FOOT_START = 0.76f
private const val LIGHT_FOOT_ALPHA = 0.28f
