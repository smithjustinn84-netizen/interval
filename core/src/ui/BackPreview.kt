package dev.justinsmith.interval.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.flow.MutableStateFlow

// Android's back preview (Material 3): while the finger is down the page shrinks, rounds its
// corners and drifts with the swipe, and the page underneath slides in beneath it. Releasing past
// the threshold finishes the pop; letting go early springs the page back.

/** Matches material3's default effects spring, which NavDisplay's own predictive pop uses. */
private fun <T> backPreviewSpring() = spring<T>(dampingRatio = 1f, stiffness = 1600f)

/** NavDisplay's `predictivePopTransitionSpec` for [backPreviewCorners]' Material back preview. */
fun <T : Any> backPreviewTransition(): AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform = { edge ->
    // Swiping in from the right edge mirrors everything.
    val direction = if (edge == NavigationEvent.EDGE_RIGHT) -1 else 1
    ContentTransform(
        targetContentEnter = fadeIn(backPreviewSpring()) +
            slideInHorizontally(backPreviewSpring()) { -(it * Motion.BACK_PREVIEW_REVEAL_SHIFT).toInt() * direction },
        initialContentExit = scaleOut(backPreviewSpring(), targetScale = Motion.BACK_PREVIEW_SCALE) +
            slideOutHorizontally(backPreviewSpring()) { (it * Motion.BACK_PREVIEW_SHIFT).toInt() * direction },
        // The page beneath stays beneath while the leaving one shrinks off it.
        targetContentZIndex = -1f,
    )
}

/**
 * Rounds the leaving page's corners while a back swipe is in progress, the half of the back
 * preview a [ContentTransform] cannot draw. Only the page NavDisplay is popping rounds: a swipe
 * another handler takes (the workout's end dialog) leaves every page square.
 */
@Composable
fun <T : Any> backPreviewCorners(): NavEntryDecorator<T> = remember {
    NavEntryDecorator { entry -> BackPreviewCorners { entry.Content() } }
}

@Composable
private fun BackPreviewCorners(content: @Composable () -> Unit) {
    val leaving = LocalNavAnimatedContentScope.current.transition.targetState == EnterExitState.PostExit
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    val gesture by remember(dispatcher) {
        dispatcher?.transitionState ?: MutableStateFlow(NavigationEventTransitionState.Idle)
    }.collectAsState()
    val swiping = leaving && gesture is NavigationEventTransitionState.InProgress
    val corner by animateDpAsState(
        if (swiping) Radius.radius24 else 0.dp,
        backPreviewSpring(),
        label = "backPreviewCorner",
    )
    // A page at rest is not clipped at all, so nothing drawn past its bounds is cut off.
    Box(
        modifier = if (corner > 0.dp) Modifier.clip(RoundedCornerShape(corner)) else Modifier,
        propagateMinConstraints = true,
    ) { content() }
}
