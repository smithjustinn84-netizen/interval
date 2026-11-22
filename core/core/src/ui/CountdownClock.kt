package dev.justinsmith.interval.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import dev.justinsmith.interval.platform.prefersReducedMotion

/**
 * A countdown's digits ([text]) on the workout's ground, or in [ink] on a surface. The clock pops as a new
 * interval starts (a change of [intervalKey]) and, smaller, on each of its last seconds while [countingDown]
 * (the [rememberCountdownPulse] the ring swells to), in step with the beeps, when its colour also goes to
 * the [accent]; otherwise it reads in the ink.
 *
 * With [heat] (0 to 1, a [RingDial]'s reading of the interval's last seconds) the accent comes before the
 * countdown does: it rises through the digits from their foot as the heat grows, so they are part filled as the
 * beeps start and fill then. Each of those seconds also flashes them brighter as it lands. Both are a shader's
 * ([CLOCK_SKSL]), painted into the digits where they are drawn, so nothing of it lays out or recomposes; where
 * the platform has none the digits go from the ink to the accent whole, as the countdown starts.
 */
@Composable
fun CountdownClock(
    text: String,
    intervalKey: Any?,
    countingDown: Boolean,
    accent: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    ink: Color = IntervalTheme.workout.onGround,
    heat: () -> Float = { 0f },
) {
    val reducedMotion = prefersReducedMotion()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(intervalKey, reducedMotion) {
        if (!reducedMotion) {
            scale.snapTo(Motion.CLOCK_POP_SCALE)
            scale.animateTo(1f, tween(Motion.CLOCK_POP_MILLIS))
        } else {
            scale.snapTo(1f)
        }
    }
    val pulse = rememberCountdownPulse(text, countingDown)
    val counting = animateFloatAsState(
        if (countingDown) 1f else 0f,
        tween(Motion.STATE_MILLIS, easing = StandardEasing),
        label = "clock",
    )
    val fill = remember { shaderProgram(CLOCK_SKSL)?.let(::ClockShaderPaint) }
    // Read through a derived state: the heat is nothing for most of an interval, and digits that aren't heating
    // aren't drawn again at each tick of the progress it is worked out from.
    val latestHeat by rememberUpdatedState(heat)
    val heated = remember { derivedStateOf { latestHeat() } }
    Text(
        text,
        // The digits are drawn as a mask, in any opaque colour, and their ink painted into them.
        style = style,
        color = Color.Black,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .graphicsLayer {
                val popped = scale.value * (1f + pulse() * (Motion.COUNTDOWN_POP_SCALE - 1f))
                scaleX = popped
                scaleY = popped
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                if (fill != null) {
                    val risen = maxOf(counting.value, heated.value * HEAT_FILL)
                    with(fill) { drawInk(ink, accent, risen, beat = pulse()) }
                } else {
                    drawRect(lerp(ink, accent, counting.value), blendMode = BlendMode.SrcIn)
                }
            },
    )
}

/** How much of the digits the accent has risen through at full heat, before the countdown fills the rest. */
private const val HEAT_FILL = 0.6f

/**
 * A clock's digits' ink: `ink` with `accent` risen through it from the foot to `fill` (0 none of it, 1 all), the
 * line between them soft, and all of it exposed brighter by `beat` ([SKSL_LIGHT]). Colours are sRGB with their
 * alpha apart; `size` is the digits' box in pixels.
 */
internal const val CLOCK_SKSL = """
uniform float2 size;
uniform float fill;
uniform float beat;
uniform float4 ink;
uniform float4 accent;
$SKSL_LIGHT
half4 main(float2 p) {
    float line = mix(0.94, 0.06, fill);
    float4 color = mix(ink, accent, smoothstep(line - 0.04, line + 0.04, p.y / size.y));
    float3 lit = exposed(color.rgb, 0.4 * beat);
    lit = clamp(lit + grain(p) / 255.0, 0.0, 1.0) * color.a;
    return half4(lit.r, lit.g, lit.b, color.a);
}
"""

/** Paints [CLOCK_SKSL] into whatever is drawn already: the digits, as their mask. */
private class ClockShaderPaint(private val program: ShaderProgram) {
    fun DrawScope.drawInk(ink: Color, accent: Color, fill: Float, beat: Float) {
        program.set("size", size.width, size.height)
        program.set("fill", fill)
        program.set("beat", beat)
        program.set("ink", ink)
        program.set("accent", accent)
        drawRect(program.brush(), blendMode = BlendMode.SrcIn)
    }
}

/**
 * The pop of an interval's last seconds, which [CountdownClock] and the ring round it share: 1 as each
 * second of [text] arrives while [countingDown], falling to 0 over [Motion.CLOCK_POP_MILLIS]; 0 at rest,
 * and always under reduced motion. A provider, so it is read where it is drawn (a `graphicsLayer`, the ring's
 * stroke) and the pop's frames recompose nothing.
 */
@Composable
fun rememberCountdownPulse(text: String, countingDown: Boolean): () -> Float {
    val reducedMotion = prefersReducedMotion()
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(text, countingDown, reducedMotion) {
        if (countingDown && !reducedMotion) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(Motion.CLOCK_POP_MILLIS))
        }
    }
    return remember(pulse) { { pulse.value } }
}

/**
 * The clock fills the share of the ring its digits allow, up to [max], the clock style's size: "0:14"
 * in a phone's ring comes out around 160sp, as the design canvas draws it, and "10:00" smaller.
 * Converted through [LocalDensity] so large system font scales never overflow the ring's geometry.
 */
@Composable
@ReadOnlyComposable
fun ringClockSize(diameter: Dp, chars: Int, max: TextUnit): TextUnit = with(LocalDensity.current) {
    val target = diameter * RING_CLOCK_SHARE / (chars * CLOCK_EMS_PER_CHAR)
    minOf(target.toPx(), max.toPx()).toSp()
}

/** How much of the ring's width the clock may take, leaving the stroke and some air. */
private const val RING_CLOCK_SHARE = 0.82f

/** Width per clock character in Barlow Condensed ExtraBold. */
private const val CLOCK_EMS_PER_CHAR = 0.46f
