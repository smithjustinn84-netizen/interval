package dev.justinsmith.lift.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.domain.standardBells

/**
 * Visual kettlebell rack showing the user's [owned] bells in ascending order, each silhouette scaled by its weight
 * and resting on a shared baseline. Decorative: the toggle grid beneath carries the interactive controls and labels.
 */
@Composable
fun KettlebellRack(owned: Set<Double>, modifier: Modifier = Modifier) {
    val bells = remember(owned) { owned.sorted() }
    if (bells.isEmpty()) return
    val palette = IntervalTheme.palette
    val minKg = standardBells.first()
    val maxKg = standardBells.last()
    Column(
        modifier.fillMaxWidth().clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            bells.forEach { kg ->
                val ratio = ((kg - minKg) / (maxKg - minKg)).toFloat().coerceIn(0f, 1f)
                KettlebellSlot(
                    kg = kg,
                    scale = MIN_BELL_SCALE + (1f - MIN_BELL_SCALE) * ratio,
                    bodyColor = palette.text,
                    bandColor = palette.accent,
                    modifier = Modifier.weight(1f, fill = false).widthIn(max = MAX_BELL_SLOT),
                )
            }
        }
    }
}

@Composable
private fun KettlebellSlot(
    kg: Double,
    scale: Float,
    bodyColor: Color,
    bandColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        Spacer(
            Modifier.fillMaxWidth().height(LiftSizes.kettlebellRack).drawWithCache {
                kettlebell(scale, bodyColor, bandColor)
            },
        )
        Text(bellNumber(kg), style = IntervalTheme.type.caption, color = IntervalTheme.palette.textDim)
    }
}

/** The bell drawn at [scale] of the slot's height: its paths built once a size, then drawn as they stand. */
private fun CacheDrawScope.kettlebell(scale: Float, bodyColor: Color, bandColor: Color): DrawResult {
    val bellHeight = size.height * scale
    val maxWidth = size.width * BELL_WIDTH_SHARE
    val bodyRadius = minOf(bellHeight * BODY_RADIUS_SHARE, maxWidth / 2f)
    val centerX = size.width / 2f
    val bottomY = size.height - BASE_PAD_PX
    val bodyCenterY = bottomY - bodyRadius * FLAT_BASE_RATIO

    // Arched handle rising from the shoulders of the bell
    val handleWidth = bodyRadius * HANDLE_WIDTH_RATIO
    val handleTopY = bodyCenterY - bodyRadius * HANDLE_HEIGHT_RATIO
    val handleStroke = (bodyRadius * HANDLE_STROKE_RATIO).coerceAtLeast(MIN_HANDLE_PX)
    val handlePath = Path().apply {
        moveTo(centerX - handleWidth, bodyCenterY - bodyRadius * HANDLE_JOIN_RATIO)
        cubicTo(
            centerX - handleWidth * HANDLE_CURVE_X,
            handleTopY,
            centerX + handleWidth * HANDLE_CURVE_X,
            handleTopY,
            centerX + handleWidth,
            bodyCenterY - bodyRadius * HANDLE_JOIN_RATIO,
        )
    }
    val handleStyle = Stroke(width = handleStroke, cap = StrokeCap.Round)

    // Spherical body with flat resting base
    val baseHalfWidth = bodyRadius * BASE_HALF_RATIO
    val bodyPath = Path().apply {
        moveTo(centerX - baseHalfWidth, bottomY)
        cubicTo(
            centerX - bodyRadius * BODY_BULGE_RATIO,
            bodyCenterY + bodyRadius * BODY_LOWER_CURVE,
            centerX - bodyRadius * BODY_BULGE_RATIO,
            bodyCenterY - bodyRadius * BODY_UPPER_CURVE,
            centerX,
            bodyCenterY - bodyRadius,
        )
        cubicTo(
            centerX + bodyRadius * BODY_BULGE_RATIO,
            bodyCenterY - bodyRadius * BODY_UPPER_CURVE,
            centerX + bodyRadius * BODY_BULGE_RATIO,
            bodyCenterY + bodyRadius * BODY_LOWER_CURVE,
            centerX + baseHalfWidth,
            bottomY,
        )
        close()
    }

    // Subtle accent band across the middle of the bell
    val bandHeight = (bodyRadius * BAND_HEIGHT_RATIO).coerceAtLeast(MIN_BAND_PX)
    val bandHalf = bodyRadius * BAND_HALF_WIDTH_RATIO
    val bandAt = Offset(centerX - bandHalf, bodyCenterY - bandHeight / 2f)
    val bandSize = Size(bandHalf * 2f, bandHeight)
    return onDrawBehind {
        drawPath(handlePath, color = bodyColor, style = handleStyle)
        drawPath(bodyPath, color = bodyColor)
        drawRect(color = bandColor, topLeft = bandAt, size = bandSize)
    }
}

private const val MIN_BELL_SCALE = 0.56f
private val MAX_BELL_SLOT = 52.dp
private const val BELL_WIDTH_SHARE = 0.90f
private const val BODY_RADIUS_SHARE = 0.34f
private const val BASE_PAD_PX = 2f
private const val FLAT_BASE_RATIO = 0.88f
private const val HANDLE_WIDTH_RATIO = 0.68f
private const val HANDLE_HEIGHT_RATIO = 1.85f
private const val HANDLE_STROKE_RATIO = 0.28f
private const val MIN_HANDLE_PX = 3f
private const val HANDLE_JOIN_RATIO = 0.62f
private const val HANDLE_CURVE_X = 0.92f
private const val BASE_HALF_RATIO = 0.68f
private const val BODY_BULGE_RATIO = 1.22f
private const val BODY_LOWER_CURVE = 0.42f
private const val BODY_UPPER_CURVE = 0.96f
private const val BAND_HEIGHT_RATIO = 0.22f
private const val MIN_BAND_PX = 2.5f
private const val BAND_HALF_WIDTH_RATIO = 0.84f

private val previewBells = setOf(12.0, 16.0, 20.0, 24.0, 32.0)

@PreviewFormFactors
@Composable
private fun KettlebellRackPreview() {
    IntervalTheme {
        ListCard(Modifier.padding(Spacing.space16)) {
            KettlebellRack(previewBells, Modifier.padding(Spacing.space16))
        }
    }
}
