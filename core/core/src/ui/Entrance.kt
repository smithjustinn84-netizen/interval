package dev.justinsmith.interval.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

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
