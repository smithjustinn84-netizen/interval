package dev.justinsmith.lift.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.domain.DEFAULT_BAR_KG
import dev.justinsmith.lift.domain.platesPerSide

/**
 * Symmetric barbell diagram showing the plates per side for [totalKg] on a [barKg] bar in competition colours
 * (25 red, 20 blue, 15 yellow, 10 green, 5 white, 2.5 dark, 1.25 silver), scaled by plate diameter and thickness.
 * Decorative: paired with [platesLine] for screen readers.
 */
@Composable
fun BarbellDiagram(
    totalKg: Double,
    barKg: Double,
    modifier: Modifier = Modifier,
) {
    val plates = remember(totalKg, barKg) { platesPerSide(totalKg, barKg) } ?: return
    val palette = IntervalTheme.palette
    val barColor = palette.textDim.copy(alpha = BAR_ALPHA)
    val collarColor = palette.textDim.copy(alpha = COLLAR_ALPHA)
    val plateBorder = palette.background.copy(alpha = PLATE_BORDER_ALPHA)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(LiftSizes.barbellDiagram)
            .clearAndSetSemantics {},
    ) {
        drawBarbell(plates, barColor, collarColor, plateBorder)
    }
}

private fun DrawScope.drawBarbell(
    plates: List<Double>,
    barColor: Color,
    collarColor: Color,
    plateBorder: Color,
) {
    val centerY = size.height / 2f
    val centerX = size.width / 2f
    val maxPlateHeight = size.height * MAX_PLATE_HEIGHT_SHARE
    val shaftHalf = size.width * SHAFT_HALF_SHARE
    val sleeveLength = (centerX - shaftHalf - END_MARGIN_SHARE * size.width).coerceAtLeast(0f)
    val shaftHeight = (size.height * SHAFT_HEIGHT_SHARE).coerceAtLeast(MIN_SHAFT_PX)
    val sleeveHeight = shaftHeight * SLEEVE_HEIGHT_RATIO
    val collarWidth = (size.width * COLLAR_WIDTH_SHARE).coerceAtLeast(MIN_COLLAR_PX)
    val collarHeight = shaftHeight * COLLAR_HEIGHT_RATIO

    // Center shaft
    drawRoundRect(
        color = barColor,
        topLeft = Offset(centerX - shaftHalf, centerY - shaftHeight / 2f),
        size = Size(shaftHalf * 2f, shaftHeight),
        cornerRadius = CornerRadius(shaftHeight / 2f),
    )

    // Left and right sleeves + collars
    val leftSleeveStart = centerX - shaftHalf - collarWidth - sleeveLength
    val rightSleeveStart = centerX + shaftHalf + collarWidth
    val sleeveRadius = CornerRadius(sleeveHeight / 2f)
    drawRoundRect(
        color = barColor,
        topLeft = Offset(leftSleeveStart, centerY - sleeveHeight / 2f),
        size = Size(sleeveLength, sleeveHeight),
        cornerRadius = sleeveRadius,
    )
    drawRoundRect(
        color = barColor,
        topLeft = Offset(rightSleeveStart, centerY - sleeveHeight / 2f),
        size = Size(sleeveLength, sleeveHeight),
        cornerRadius = sleeveRadius,
    )
    val collarRadius = CornerRadius(collarWidth / 2f)
    drawRoundRect(
        color = collarColor,
        topLeft = Offset(centerX - shaftHalf - collarWidth, centerY - collarHeight / 2f),
        size = Size(collarWidth, collarHeight),
        cornerRadius = collarRadius,
    )
    drawRoundRect(
        color = collarColor,
        topLeft = Offset(centerX + shaftHalf, centerY - collarHeight / 2f),
        size = Size(collarWidth, collarHeight),
        cornerRadius = collarRadius,
    )

    if (plates.isEmpty()) return

    val specs = plates.map(::plateSpec)
    val gap = (size.width * PLATE_GAP_SHARE).coerceAtLeast(MIN_GAP_PX)
    val rawWidth = specs.sumOf { it.thickness.toDouble() }.toFloat() * size.width +
        gap * (specs.size - 1).coerceAtLeast(0)
    val maxLoadedWidth = sleeveLength * MAX_SLEEVE_FILL_SHARE
    val fit = if (rawWidth > maxLoadedWidth && rawWidth > 0f) maxLoadedWidth / rawWidth else 1f
    val fittedGap = gap * fit

    var offset = 0f
    for (spec in specs) {
        val w = (spec.thickness * size.width * fit).coerceAtLeast(MIN_PLATE_PX)
        val h = maxPlateHeight * spec.height
        val radius = CornerRadius(w * PLATE_CORNER_RATIO)
        val top = centerY - h / 2f
        val rightX = rightSleeveStart + offset
        val leftX = centerX - shaftHalf - collarWidth - offset - w
        drawPlate(leftX, top, w, h, radius, spec.color, plateBorder)
        drawPlate(rightX, top, w, h, radius, spec.color, plateBorder)
        offset += w + fittedGap
    }
}

private fun DrawScope.drawPlate(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    radius: CornerRadius,
    fill: Color,
    border: Color,
) {
    drawRoundRect(color = fill, topLeft = Offset(x, y), size = Size(width, height), cornerRadius = radius)
    drawRoundRect(
        color = border,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = radius,
        style = Stroke(width = PLATE_BORDER_PX),
    )
}

private data class PlateSpec(val color: Color, val height: Float, val thickness: Float)

private fun plateSpec(kg: Double): PlateSpec = when (kg) {
    PLATE_25 -> PlateSpec(PlateRed, height = 1.00f, thickness = 0.036f)
    PLATE_20 -> PlateSpec(PlateBlue, height = 1.00f, thickness = 0.032f)
    PLATE_15 -> PlateSpec(PlateYellow, height = 0.88f, thickness = 0.028f)
    PLATE_10 -> PlateSpec(PlateGreen, height = 0.78f, thickness = 0.025f)
    PLATE_5 -> PlateSpec(PlateWhite, height = 0.60f, thickness = 0.022f)
    PLATE_2_5 -> PlateSpec(PlateDark, height = 0.48f, thickness = 0.019f)
    else -> PlateSpec(PlateSilver, height = 0.38f, thickness = 0.016f)
}

private const val PLATE_25 = 25.0
private const val PLATE_20 = 20.0
private const val PLATE_15 = 15.0
private const val PLATE_10 = 10.0
private const val PLATE_5 = 5.0
private const val PLATE_2_5 = 2.5

private val PlateRed = Color(0xFFD63638)
private val PlateBlue = Color(0xFF2B6CB0)
private val PlateYellow = Color(0xFFD69E2E)
private val PlateGreen = Color(0xFF2F855A)
private val PlateWhite = Color(0xFFE2E8F0)
private val PlateDark = Color(0xFF4A5568)
private val PlateSilver = Color(0xFFA0AEC0)

private const val BAR_ALPHA = 0.45f
private const val COLLAR_ALPHA = 0.65f
private const val PLATE_BORDER_ALPHA = 0.55f
private const val MAX_PLATE_HEIGHT_SHARE = 0.90f
private const val SHAFT_HALF_SHARE = 0.21f
private const val END_MARGIN_SHARE = 0.03f
private const val SHAFT_HEIGHT_SHARE = 0.11f
private const val MIN_SHAFT_PX = 4f
private const val SLEEVE_HEIGHT_RATIO = 1.45f
private const val COLLAR_WIDTH_SHARE = 0.022f
private const val MIN_COLLAR_PX = 5f
private const val COLLAR_HEIGHT_RATIO = 2.5f
private const val PLATE_GAP_SHARE = 0.008f
private const val MIN_GAP_PX = 1.5f
private const val MAX_SLEEVE_FILL_SHARE = 0.90f
private const val MIN_PLATE_PX = 4f
private const val PLATE_CORNER_RATIO = 0.32f
private const val PLATE_BORDER_PX = 1f

@PreviewFormFactors
@Composable
private fun BarbellDiagramPreview() {
    IntervalTheme {
        Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
            BarbellDiagram(totalKg = 102.5, barKg = DEFAULT_BAR_KG)
            BarbellDiagram(totalKg = 140.0, barKg = DEFAULT_BAR_KG)
            BarbellDiagram(totalKg = DEFAULT_BAR_KG, barKg = DEFAULT_BAR_KG)
        }
    }
}
