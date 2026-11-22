package dev.justinsmith.interval.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import dev.justinsmith.interval.platform.prefersReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.hypot

// A shockwave: one hard-edged front out from a point, fast then slowing, with what it leaves behind it. Where a
// Burst's rings are a moment rippling over the screen, this is the blow itself: shorter, thinner, and gone first.

/**
 * A moment struck: one front of [color] out from the centre to [reach] (past the corners, unless the moment keeps
 * it nearer) over [Motion.SHOCKWAVE_MILLIS], crisp on the side it is going and falling away behind, thinning and
 * fading as it goes. Once, [delayMillis] after it is composed, behind whatever it marks; nothing at all under
 * reduced motion. Its front starts [stroke] wide: a screen's or a ring's at [Sizes.shockwaveStroke], a row's at
 * [Sizes.rowShockwaveStroke].
 *
 * Lift plays it on a set that tops a ladder and as an EMOM's last set is done; a record's has it leading the
 * [Burst] on its row and out from the bell its figure raises ([Celebration.shockAt]).
 */
@Composable
fun Shockwave(
    color: Color,
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    stroke: Dp = Sizes.shockwaveStroke,
    reach: Dp = Dp.Unspecified,
) {
    if (prefersReducedMotion()) return
    val spent = remember { Animatable(0f) }
    val program = remember { shaderProgram(SHOCKWAVE_SKSL) }
    val hot = onDarkGround()
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        spent.animateTo(1f, tween(Motion.SHOCKWAVE_MILLIS, easing = LinearEasing))
    }
    Canvas(modifier.clearAndSetSemantics {}) {
        val far = if (reach.isSpecified) reach.toPx() else hypot(size.width, size.height) / 2
        drawShockwave(program, spent.value, color, center, far, stroke.toPx(), hot)
    }
}

/**
 * Whether the theme's ground is a dark one, where a shockwave's front is drawn [hot][drawShockwave]: more light
 * on a dark ground stands out, and on a light one would wash the colour out toward the ground.
 */
@Composable
@ReadOnlyComposable
internal fun onDarkGround(): Boolean = IntervalTheme.palette.background.luminance() < MID_LUMINANCE

/**
 * A shockwave [spent] of the way through, 0 to 1, out from [origin] toward [reach] with a front that set out
 * [stroke] wide: painted by [program], or with none (a platform without shaders) as a ring and the wake behind it.
 * [hot] has the shader's front brighter than its colour, for a dark ground. Nothing before it sets out or once it
 * is spent.
 */
internal fun DrawScope.drawShockwave(
    program: ShaderProgram?,
    spent: Float,
    color: Color,
    origin: Offset,
    reach: Float,
    stroke: Float,
    hot: Boolean,
) {
    if (spent <= 0f || spent >= 1f || reach <= 0f) return
    val radius = reach * shockwaveSpread(spent)
    val halfWidth = stroke * shockwaveWidth(spent) / 2
    val wake = reach * WAKE_SHARE
    val strength = shockwaveStrength(spent)
    if (program != null) {
        program.set("uCenter", origin.x, origin.y)
        program.set("uRadius", radius)
        program.set("uHalfWidth", halfWidth)
        program.set("uWake", wake)
        program.set("uStrength", strength)
        program.set("uGain", if (hot) HOT_GAIN else 0f)
        program.set("uColor", color)
        drawRect(program.brush())
        return
    }
    // Not until it is wider than its stroke, which would otherwise fill it in.
    if (radius > halfWidth * 2) drawCircle(color, radius, origin, alpha = strength, style = Stroke(halfWidth * 2))
    val echo = radius - wake * ECHO_SHARE
    if (echo > halfWidth) {
        drawCircle(color, echo, origin, alpha = strength * ECHO_STRENGTH, style = Stroke(halfWidth))
    }
}

/**
 * How far out the front is, as a share of its reach, [spent] of the way through: most of the way in the first
 * third, then slowing to a stop, as a blast does.
 */
internal fun shockwaveSpread(spent: Float): Float {
    val left = 1f - spent.coerceIn(0f, 1f)
    return 1f - left * left * left
}

/** How wide the front still is, as a share of the stroke it set out with: thinning to [THINNEST] of it. */
internal fun shockwaveWidth(spent: Float): Float = 1f - (1f - THINNEST) * spent.coerceIn(0f, 1f)

/**
 * How strong it is, 0 to 1: there at once (in over [EMERGE] of the run, so its first frame is no flash), holding
 * while the front is travelling and gone as it stops, so it is never a faint ring left standing at its reach.
 */
internal fun shockwaveStrength(spent: Float): Float {
    val at = spent.coerceIn(0f, 1f)
    val risen = (at / EMERGE).coerceAtMost(1f)
    return risen * risen * (SMOOTH - 2f * risen) * (1f - at * at)
}

/**
 * The shockwave, in the language SkSL and AGSL share: the front a band at `uRadius`, hard on the side it is going
 * (nothing is reached before it arrives) with a short shoulder inside; behind it the wake, falling away over
 * `uWake`, and an echo of the front riding halfway down it. The front is `exposed` by `uGain`, hotter than its
 * colour, so on a dark ground it reads as light rather than paint. Returns premultiplied RGBA.
 */
internal const val SHOCKWAVE_SKSL: String = SKSL_LIGHT + """
uniform float2 uCenter;
uniform float uRadius;
uniform float uHalfWidth;
uniform float uWake;
uniform float uStrength;
uniform float uGain;
uniform float4 uColor;

half4 main(float2 fragCoord) {
    float behind = uRadius - length(fragCoord - uCenter);
    float aa = 0.75;

    float outer = 1.0 - smoothstep(uHalfWidth - aa, uHalfWidth + aa, -behind);
    float inner = 1.0 - smoothstep(uHalfWidth, uHalfWidth * 2.5 + aa, behind);
    float front = outer * inner;

    float trail = clamp(1.0 - behind / max(uWake, 1.0), 0.0, 1.0);
    float wake = step(0.0, behind) * trail * trail * 0.2;

    float echoWidth = uHalfWidth * 0.5;
    float echo = 1.0 - smoothstep(echoWidth - aa, echoWidth + aa, abs(behind - uWake * 0.5));

    float lit = max(front, max(wake, echo * 0.35));
    float alpha = clamp(lit * uStrength, 0.0, 1.0) * uColor.a;
    alpha = clamp(alpha + grain(fragCoord) / 255.0 * step(0.004, alpha), 0.0, 1.0);
    float3 color = exposed(uColor.rgb, uGain * front * uStrength);
    return half4(color * alpha, alpha);
}
"""

/** The wake behind the front is this share of the reach long; the echo rides this far down it, this strong. */
private const val WAKE_SHARE = 0.28f
private const val ECHO_SHARE = 0.5f
private const val ECHO_STRENGTH = 0.35f

/** The front thins to this share of its stroke, and is at full strength this far into its run. */
private const val THINNEST = 0.4f
private const val EMERGE = 0.08f
private const val SMOOTH = 3f

/** How much more light the front has than its colour, on a dark ground. */
private const val HOT_GAIN = 0.7f
private const val MID_LUMINANCE = 0.5f
