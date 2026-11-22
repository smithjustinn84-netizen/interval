package dev.justinsmith.interval.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import dev.justinsmith.interval.platform.prefersReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * How far a card leans under the finger: [lean] is −1 to 1 each way, from its start and top edges pressed to its
 * end and foot, and nothing at rest. It follows the finger on a quick spring and settles back when let go. Read
 * where the card is drawn ([tilting], [sheen]), so leaning recomposes nothing.
 */
@Stable
class Tilt internal constructor(private val scope: CoroutineScope, initial: Offset) {
    private val leaning = Animatable(initial, Offset.VectorConverter)

    val lean: Offset get() = leaning.value

    internal fun leanTo(to: Offset) {
        scope.launch { leaning.animateTo(to, spring(dampingRatio = Spring.DampingRatioNoBouncy, FOLLOW_STIFFNESS)) }
    }

    internal fun settle() {
        scope.launch { leaning.animateTo(Offset.Zero, settleSpring()) }
    }
}

/** A [Tilt] at rest, or for a preview leaning by [initial]. */
@Composable
fun rememberTilt(initial: Offset = Offset.Zero): Tilt {
    val scope = rememberCoroutineScope()
    return remember { Tilt(scope, initial) }
}

/**
 * Leans what it is on under the finger, the edge pressed going back as a card on a table would, by up to
 * [TILT_DEGREES], and lets it settle when the finger lifts. It takes nothing of the touch, so a tap or a scroll
 * over it goes on as it would. Under reduced motion it stays flat.
 */
@Composable
fun Modifier.tilting(tilt: Tilt): Modifier {
    val enabled = !prefersReducedMotion()
    return pointerInput(tilt, enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            try {
                tilt.leanTo(leanAt(down.position, size))
                do {
                    val event = awaitPointerEvent()
                    val finger = event.changes.firstOrNull { it.id == down.id }
                    if (finger != null && finger.pressed) tilt.leanTo(leanAt(finger.position, size))
                } while (event.changes.any { it.pressed })
            } finally {
                tilt.settle()
            }
        }
    }.graphicsLayer {
        val lean = tilt.lean
        rotationY = lean.x * TILT_DEGREES
        rotationX = -lean.y * TILT_DEGREES
        cameraDistance = TILT_CAMERA
    }
}

/** The lean of a touch at [position] on a card of [size]: nothing at its middle, 1 at an edge and no more past it. */
internal fun leanAt(position: Offset, size: IntSize): Offset {
    if (size.width <= 0 || size.height <= 0) return Offset.Zero
    return Offset(
        (position.x / size.width * 2f - 1f).coerceIn(-1f, 1f),
        (position.y / size.height * 2f - 1f).coerceIn(-1f, 1f),
    )
}

/**
 * A sheen over what is drawn, as light off a glossy card: a soft band of [light] on a slant, lying toward the top
 * at rest and sliding across with the [tilt], toward the edge pressed. Put it after the clip of the card's shape.
 * A shader's ([SHEEN_SKSL]) where the platform has one, else a plain gradient of the same band.
 */
@Composable
fun Modifier.sheen(tilt: Tilt, light: Color = Color.White.copy(alpha = Opacity.SHEEN)): Modifier {
    val paint = remember { shaderProgram(SHEEN_SKSL)?.let(::SheenPaint) }
    return drawWithContent {
        drawContent()
        val lean = tilt.lean
        if (paint != null) with(paint) { drawSheen(lean, light) } else drawPlainSheen(lean, light)
    }
}

/** The sheen's slant, a unit vector; where along it the band lies at rest, and how far a full lean slides it. */
private const val SLANT_X = 0.6f
private const val SLANT_Y = 0.8f
private const val SHEEN_REST = 0.3f
private const val SHEEN_TRAVEL = 0.42f

/**
 * A card's sheen: a narrow band of `light` over a broad faint wash of it, both lying across the card on a slant
 * and centred where `lean` (−1 to 1 each way) puts them, toward the top at rest. `light`'s alpha is the band's
 * strength at its brightest; a grain breaks the wash's bands. `size` is the card's box in pixels.
 */
internal const val SHEEN_SKSL = """
uniform float2 size;
uniform float2 lean;
uniform float4 light;
$SKSL_LIGHT
half4 main(float2 p) {
    float2 at = p / size - 0.5;
    float2 slant = float2($SLANT_X, $SLANT_Y);
    float off = dot(at, slant) + $SHEEN_REST - dot(lean, slant) * $SHEEN_TRAVEL;
    float band = exp(-off * off / 0.010);
    float wash = exp(-off * off / 0.14);
    float a = light.a * (0.62 * band + 0.38 * wash);
    a = clamp(a + grain(p) / 255.0 * step(0.004, a), 0.0, 1.0);
    return half4(light.r * a, light.g * a, light.b * a, a);
}
"""

/** Paints [SHEEN_SKSL] over a card. */
private class SheenPaint(private val program: ShaderProgram) {
    fun DrawScope.drawSheen(lean: Offset, light: Color) {
        program.set("size", size.width, size.height)
        program.set("lean", lean.x, lean.y)
        program.set("light", light)
        drawRect(program.brush())
    }
}

/** The sheen without its shader: the band alone, a gradient across the same slant. */
private fun DrawScope.drawPlainSheen(lean: Offset, light: Color) {
    val centre = -SHEEN_REST + (lean.x * SLANT_X + lean.y * SLANT_Y) * SHEEN_TRAVEL
    val middle =
        Offset(size.width / 2 + size.width * centre * SLANT_X, size.height / 2 + size.height * centre * SLANT_Y)
    val reach = Offset(size.width * SLANT_X, size.height * SLANT_Y) * PLAIN_BAND
    drawRect(
        Brush.linearGradient(
            listOf(Color.Transparent, light.copy(alpha = light.alpha * PLAIN_STRENGTH), Color.Transparent),
            start = middle - reach,
            end = middle + reach,
        ),
    )
}

/** The most a card leans, in degrees, and how far off the eye is that sees it lean (the layer's camera). */
private const val TILT_DEGREES = 9f
private const val TILT_CAMERA = 12f

/** The spring a lean follows the finger on: quick, with no bounce to fight the finger. */
private const val FOLLOW_STIFFNESS = 600f

/** The plain sheen's band: half its width as a share of the card, and its strength beside the shader's. */
private const val PLAIN_BAND = 0.28f
private const val PLAIN_STRENGTH = 0.7f
