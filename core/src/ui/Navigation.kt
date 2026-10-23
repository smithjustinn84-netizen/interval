package dev.justinsmith.interval.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import dev.justinsmith.interval.platform.platformPageTransitions

/**
 * The navigation area for a window of [width] × [height]: a bottom bar only for portrait windows
 * narrower than [Sizes.railMinWidth], a rail otherwise. Material's default would also give a phone
 * held sideways a bar, since its height is compact; the rail keeps the little height there is.
 */
fun navigationSuiteType(width: Dp, height: Dp): NavigationSuiteType {
    val rail = width >= Sizes.railMinWidth || width > height
    return if (rail) NavigationSuiteType.NavigationRail else NavigationSuiteType.NavigationBar
}

/**
 * NavDisplay's `transitionSpec`. How a page moves says what kind of page it is:
 * - a page one level deeper (Settings, About, Licenses) slides in from the end as the page beneath
 *   drifts away under it, the page beneath fading out quickly before the new one fades in;
 * - the workout, a mode of its own rather than a page, rises over the app as the app sinks back;
 * - from one tab to another the old fades out, then the new fades in a touch larger;
 * - Settings opening beside the Timer crossfades, the Timer staying where it is.
 * On a platform with page transitions of its own (iOS), those run instead, except for the workout:
 * rising is how iOS presents a full-screen mode too.
 */
fun <T : Any> pageTransition(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    when {
        workoutChange -> workoutRise()
        platformPageTransitions -> defaultTransitionSpec<T>()(this)
        else -> appPageTransition<T>()(this)
    }
}

/** NavDisplay's `popTransitionSpec`: the reverse of [pageTransition], the leaving page staying on top. */
fun <T : Any> pagePopTransition(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    when {
        workoutChange -> workoutDrop()
        platformPageTransitions -> defaultPopTransitionSpec<T>()(this)
        else -> appPagePopTransition<T>()(this)
    }
}

private fun <T : Any> appPageTransition(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    when {
        twoPane -> crossfade()

        tabChange -> fadeThrough(Motion.PAGE_MILLIS)

        else -> ContentTransform(
            targetContentEnter = lateFadeIn(Motion.PAGE_MILLIS) +
                slideIntoContainer(SlideDirection.Start, pageSpec()) { (it * Motion.PAGE_SLIDE_SHARE).toInt() },
            initialContentExit = quickFadeOut() +
                slideOutOfContainer(SlideDirection.Start, pageSpec()) { (it * Motion.PAGE_SLIDE_SHARE / 2).toInt() },
        )
    }
}

private fun <T : Any> appPagePopTransition(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    when {
        twoPane -> crossfade()

        tabChange -> fadeThrough(Motion.PAGE_MILLIS)

        else -> ContentTransform(
            targetContentEnter = lateFadeIn(Motion.PAGE_MILLIS) +
                slideIntoContainer(SlideDirection.End, pageSpec()) { (it * Motion.PAGE_SLIDE_SHARE / 2).toInt() },
            initialContentExit = quickFadeOut() +
                slideOutOfContainer(SlideDirection.End, pageSpec()) { (it * Motion.PAGE_SLIDE_SHARE).toInt() },
        )
    }
}

/**
 * What kind of page an entry is, which decides how it moves. Pages one level deeper need no kind:
 * they are the default. An entry's key is private to it, so the kind travels in its metadata.
 */
enum class PageKind {
    /** A destination of the navigation bar or rail. */
    Tab,

    /** A mode of its own over the app (the workout), rather than a page one level deeper. */
    Mode,
}

/** The metadata marking an entry as [kind], for `entry<X>(metadata = …)`. */
fun pageKind(kind: PageKind): Map<String, Any> = mapOf(PAGE_KIND to kind)

private const val PAGE_KIND = "dev.justinsmith.interval.pageKind"

private val Scene<*>.topKind: Any?
    get() = entries.lastOrNull()?.metadata?.get(PAGE_KIND)

/** A mode is on top of either scene: the workout is starting or ending. */
private val <T : Any> AnimatedContentTransitionScope<Scene<T>>.workoutChange: Boolean
    get() = targetState.topKind == PageKind.Mode || initialState.topKind == PageKind.Mode

/** The workout rises into place, growing from a touch smaller, as the page beneath sinks back. */
private fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.workoutRise(): ContentTransform = ContentTransform(
    targetContentEnter = fadeIn(pageSpec()) +
        slideIntoContainer(SlideDirection.Up, pageSpec()) { (it * Motion.WORKOUT_RISE_SHARE).toInt() } +
        scaleIn(pageSpec(), initialScale = Motion.FADE_THROUGH_SCALE),
    initialContentExit = fadeOut(exitSpec()) + scaleOut(pageSpec(), targetScale = Motion.FADE_THROUGH_SCALE),
)

/** The reverse of [workoutRise]: the workout drops away on top as the page beneath comes forward again. */
private fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.workoutDrop(): ContentTransform = ContentTransform(
    targetContentEnter = fadeIn(pageSpec()) + scaleIn(pageSpec(), initialScale = Motion.FADE_THROUGH_SCALE),
    initialContentExit = fadeOut(tween(Motion.PAGE_MILLIS, easing = ExitEasing)) +
        slideOutOfContainer(SlideDirection.Down, tween(Motion.PAGE_MILLIS, easing = ExitEasing)) {
            (it * Motion.WORKOUT_RISE_SHARE).toInt()
        },
    targetContentZIndex = -1f,
)

/** Both scenes are single tabs: the bar's own switch, not a push. */
private val <T : Any> AnimatedContentTransitionScope<Scene<T>>.tabChange: Boolean
    get() = targetState.topKind == PageKind.Tab && initialState.topKind == PageKind.Tab

/** Either scene shows two pages side by side (Settings beside the Timer on a wide window). */
private val <T : Any> AnimatedContentTransitionScope<Scene<T>>.twoPane: Boolean
    get() = targetState.entries.size > 1 || initialState.entries.size > 1

private fun crossfade(): ContentTransform =
    ContentTransform(fadeIn(tween(Motion.EXIT_MILLIS)), fadeOut(tween(Motion.EXIT_MILLIS)))

private fun <T> pageSpec() = tween<T>(Motion.PAGE_MILLIS, easing = EnterEasing)
private fun exitSpec() = tween<Float>(Motion.PAGE_MILLIS, easing = StandardEasing)

/** The bar's and the rail's containers in the app's colours. */
@Composable
fun navigationSuiteColors(): NavigationSuiteColors = NavigationSuiteDefaults.colors(
    navigationBarContainerColor = IntervalTheme.palette.surface,
    navigationRailContainerColor = IntervalTheme.palette.surface,
)

/** Wider than tall: where the screens put their parts side by side. */
val BoxWithConstraintsScope.isLandscape: Boolean get() = maxWidth > maxHeight
