package dev.justinsmith.interval.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import dev.justinsmith.interval.platform.prefersReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The moves a moment gets, over Motion.kt's everyday ones: a tag landing, a mark popping as something closes.

/** The spring a stamp lands on: loose enough to overshoot once. */
private fun stampSpring(): SpringSpec<Float> =
    spring(dampingRatio = Motion.STAMP_DAMPING, stiffness = Spring.StiffnessMedium)

/**
 * Stamps content in when it [play]s: it lands from [Motion.STAMP_SCALE] on a spring that overshoots once,
 * fading up as it comes, after [delayMillis]; the Record tag on a set just logged. Content not playing is
 * simply there. Under reduced motion it only fades.
 */
@Composable
fun Modifier.stampIn(play: Boolean, delayMillis: Int = 0): Modifier {
    if (!play) return this
    val reducedMotion = prefersReducedMotion()
    val scale = remember { Animatable(if (reducedMotion) 1f else Motion.STAMP_SCALE) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        launch { alpha.animateTo(1f, tween(Motion.CONTENT_MILLIS)) }
        if (!reducedMotion) scale.animateTo(1f, stampSpring())
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        this.alpha = alpha.value
    }
}

/**
 * Pops content once on each change of [key] after its first composition: it snaps to [scale] and springs
 * back, as the goal ring does as it closes and the streak's flame as the streak grows. Nothing under
 * reduced motion.
 */
@Composable
fun Modifier.popOnce(key: Any?, scale: Float): Modifier {
    val reducedMotion = prefersReducedMotion()
    val pop = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(key) {
        if (first) {
            first = false
        } else if (!reducedMotion) {
            pop.snapTo(scale)
            pop.animateTo(1f, pressSpring())
        }
    }
    return graphicsLayer {
        scaleX = pop.value
        scaleY = pop.value
    }
}
