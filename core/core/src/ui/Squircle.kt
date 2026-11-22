package dev.justinsmith.interval.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * An app icon's outline: a superellipse, its sides easing into its corners with no point where a straight edge
 * ends and an arc begins, as a home screen cuts an icon. Fills whatever box it is given.
 */
val Squircle: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(
            Path().apply {
                repeat(SQUIRCLE_POINTS) { index ->
                    val x = squircleReach(cos(TURN * index / SQUIRCLE_POINTS))
                    val y = squircleReach(sin(TURN * index / SQUIRCLE_POINTS))
                    val px = size.width / 2 * (1f + x)
                    val py = size.height / 2 * (1f + y)
                    if (index == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            },
        )

    override fun toString(): String = "Squircle"
}

/**
 * How far along an axis (−1 to 1) a squircle's edge stands where a circle's would stand at [circle]: the
 * superellipse |x|ⁿ + |y|ⁿ = 1 walked by a circle's angle, which crowds its points into the corners, where it bends.
 */
internal fun squircleReach(circle: Float): Float = sign(circle) * abs(circle).pow(2f / SQUIRCLE_POWER)

/** The superellipse's power: 2 is a circle, and the higher the squarer. Five is near a home screen's icon. */
private const val SQUIRCLE_POWER = 5f

/** Points round the outline: enough that no side of the polygon shows at a card's size. */
private const val SQUIRCLE_POINTS = 96

private const val TURN = 2f * PI.toFloat()
