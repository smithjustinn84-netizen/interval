package dev.justinsmith.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.common_back
import org.jetbrains.compose.resources.stringResource

/**
 * A pushed page that scrolls as one: Settings, About, Licenses. Its [TitleBar] and [content] share one
 * centred column of [Sizes.content] at most, inside the safe area, with the page's side and bottom
 * margins around the content.
 */
@Composable
fun ScrollPage(
    title: String,
    modifier: Modifier = Modifier,
    navigation: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(IntervalTheme.palette.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = Sizes.content)
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            TitleBar(title, navigation = navigation, actions = actions)
            Column(
                Modifier.padding(start = Spacing.space16, end = Spacing.space16, bottom = Spacing.space24),
                horizontalAlignment = horizontalAlignment,
                content = content,
            )
        }
    }
}

/** A page's Back action, for a [TitleBar]'s navigation slot. */
@Composable
fun BackAction(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconAction(IntervalIcons.Back, stringResource(Res.string.common_back), onBack, modifier)
}

/**
 * A page of cards, a tab's (History, Lift) or one pushed from it (Presets, the bells): the lanes behind, filling
 * the window rather than stopping at the column's edge, then a grid of one column on a phone and more as the
 * window widens, its [TitleBar] across the top.
 * [content] adds the grid's items under it. A pushed page passes [navigation], its Back, and keeps inside
 * the safe area itself, though its cards scroll on under the gesture bar rather than stopping above it; a tab's
 * sits inside the navigation bar's. [bottomBar] stays pinned under the grid, over the same lanes, for an action
 * that must never scroll away (Lift's Start).
 */
@Composable
fun TabGrid(
    title: String,
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: LazyStaggeredGridScope.() -> Unit,
) {
    val safe = if (navigation != null) WindowInsets.safeDrawing else WindowInsets(0)
    // With nothing pinned under it, the grid runs to the window's foot and keeps the inset as padding instead.
    val under = bottomBar == null
    val foot = safe.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxSize().background(IntervalTheme.palette.background), contentAlignment = Alignment.TopCenter) {
        SetupLanes(Modifier.matchParentSize())
        Column(
            Modifier
                .widthIn(max = Sizes.listMax)
                .fillMaxSize()
                .windowInsetsPadding(
                    if (under) safe.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top) else safe,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(Sizes.listColumn),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = if (under) Spacing.space24 + foot else Spacing.space8),
            ) {
                item(key = "title", span = StaggeredGridItemSpan.FullLine) {
                    TitleBar(title, navigation = navigation ?: {}, actions = actions)
                }
                content()
            }
            bottomBar?.invoke()
        }
    }
}
