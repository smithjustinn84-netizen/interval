package dev.justinsmith.interval.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.platform.prefersReducedMotion

// Content arriving as a screen opens: once, rising in (`risingEnter`, Motion.kt), and never leaving.

/**
 * Hidden on its first frame, then shown: `AnimatedVisibility(visibleState = rememberEntrance())`
 * plays its enter transition once, as the content first appears, and never exits.
 */
@Composable
fun rememberEntrance(): MutableTransitionState<Boolean> =
    remember { MutableTransitionState(false).apply { targetState = true } }

/**
 * For a row in a list that grows while it is on screen: a row the screen opened with is simply there
 * (not [isNew]), while one logged or saved since rises in once, as [rememberEntrance] does. Callers
 * remember the ids on show at open and pass `id !in atOpen`.
 */
@Composable
fun rememberArrival(isNew: Boolean): MutableTransitionState<Boolean> =
    if (isNew) rememberEntrance() else remember { MutableTransitionState(true) }

/**
 * One part of a page arriving in turn: [content] rises in [step] stagger steps after [entrance] (a
 * [rememberEntrance]) starts, so a summary's parts come one after another.
 */
@Composable
fun Arrival(
    entrance: MutableTransitionState<Boolean>,
    step: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(entrance, modifier, enter = risingEnter(Motion.STAGGER_MILLIS * step)) { content() }
}

/**
 * [value] counted up from nothing as its page arrives, over the time a summary's ring takes to close
 * ([Motion.SUMMARY_RING_MILLIS]); at once when not [enabled] (the same numbers on a page in History,
 * where nothing arrives) and under reduced motion, where a running number would be the one thing that moved.
 */
@Composable
fun countUp(value: Int, enabled: Boolean = true): Int {
    val reduced = prefersReducedMotion()
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val shown by animateIntAsState(
        if (started || reduced || !enabled) value else 0,
        tween(Motion.SUMMARY_RING_MILLIS, easing = EnterEasing),
        label = "countUp",
    )
    return shown
}
