package dev.justinsmith.interval.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy

/**
 * The navigation area: the bar along a phone's foot or the rail along a wider window's start. It lies over the
 * pages rather than beside them, so NavDisplay always fills the window and a page lays out once, at its size,
 * whatever the area does. A tab's scene keeps the area's room clear itself ([tabScenes]) by [thickness], the
 * size the area last laid out at, so the room stays while the area is on its way or away: the tab under a push
 * holds still as the area slides off ([NavigationAreaBar]).
 */
@Stable
class NavigationArea internal constructor(type: NavigationSuiteType) {
    /** The bar or the rail, by the window's size alone. Whether it shows is [NavigationAreaBar]'s `visible`. */
    var type: NavigationSuiteType by mutableStateOf(type)
        internal set

    val bar: Boolean get() = type == NavigationSuiteType.NavigationBar

    /** The bar's height or the rail's width in px as it last laid out, the system inset on its side included. */
    var thickness: Int by mutableIntStateOf(0)
        internal set

    /** What a tab's scene keeps inside: the system's insets on the sides the area leaves open, plus the area. */
    @Composable
    fun tabInsets(): WindowInsets = if (bar) {
        WindowInsets.safeDrawing
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            .add(WindowInsets(bottom = thickness))
    } else {
        WindowInsets.safeDrawing
            .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
            .add(WindowInsets(left = thickness, right = thickness).only(WindowInsetsSides.Start))
    }
}

/** The window's [NavigationArea], of [type]: one instance, following the type as the window resizes. */
@Composable
fun rememberNavigationArea(type: NavigationSuiteType): NavigationArea {
    val area = remember { NavigationArea(type) }
    if (area.type != type) area.type = type
    return area
}

/**
 * NavDisplay's scene decorator: a scene showing a tab, alone or with a page beside it, lays out inside [this]
 * area's [NavigationArea.tabInsets] and consumes them, so its pages see none, for as long as it is on the
 * screen. Any other scene fills the window and keeps inside the safe area itself. Remember the one instance:
 * NavDisplay keeps the scenes it has built, so a scene reads the area through its state rather than a new
 * strategy.
 */
fun <T : Any> NavigationArea.tabScenes(): SceneDecoratorStrategy<T> = SceneDecoratorStrategy { scene ->
    if (scene.entries.any { it.isTab }) TabScene(scene, this@tabScenes) else scene
}

/** [inner] inside [area]'s insets. A data class: NavDisplay tells scenes apart by `==`, their class and key. */
private data class TabScene<T : Any>(val inner: Scene<T>, val area: NavigationArea) : Scene<T> {
    override val key: Any get() = inner.key
    override val entries: List<NavEntry<T>> get() = inner.entries
    override val previousEntries: List<NavEntry<T>> get() = inner.previousEntries
    override val metadata: Map<String, Any> get() = inner.metadata
    override val content: @Composable () -> Unit = {
        val insets = area.tabInsets()
        Box(Modifier.fillMaxSize().windowInsetsPadding(insets).consumeWindowInsets(insets)) { inner.content() }
    }
}

/**
 * The area over the pages' edge: the bar along the window's foot, or the rail along its start, with [items]
 * in it. As [visible] turns false it slides off its edge and fades, in step with the page pushed over its tab,
 * and comes back the same way as the tab returns; the pages under it never move. The bar carries the hairline
 * along its top.
 */
@Composable
fun NavigationAreaBar(
    area: NavigationArea,
    visible: Boolean,
    modifier: Modifier = Modifier,
    items: @Composable () -> Unit,
) {
    val bar = area.bar
    // The rail leaves toward the start: left in LTR, right in RTL.
    val start = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1 else -1
    AnimatedVisibility(
        visible,
        modifier,
        enter = fadeIn(areaFade()) +
            (if (bar) slideInVertically(areaSlide()) { it } else slideInHorizontally(areaSlide()) { it * start }),
        exit = fadeOut(areaFade()) +
            (if (bar) slideOutVertically(areaSlide()) { it } else slideOutHorizontally(areaSlide()) { it * start }),
    ) {
        NavigationSuite(
            navigationSuiteType = area.type,
            modifier = Modifier
                .onSizeChanged { area.thickness = if (bar) it.height else it.width }
                .then(if (bar) Modifier.hairlineAtTop(IntervalTheme.palette.line) else Modifier),
            colors = navigationSuiteColors(),
            content = items,
        )
    }
}

/** The page's own pace and easing, so the area and the page move as one. */
private fun <T> areaSlide() = tween<T>(Motion.PAGE_MILLIS, easing = EnterEasing)

private fun areaFade() = tween<Float>(Motion.PAGE_MILLIS, easing = StandardEasing)

/** The line along the bar's top edge, over the foot of the content under it. */
private fun Modifier.hairlineAtTop(color: Color): Modifier = drawWithContent {
    drawContent()
    val stroke = Sizes.hairline.toPx()
    val y = stroke / 2
    drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
}
