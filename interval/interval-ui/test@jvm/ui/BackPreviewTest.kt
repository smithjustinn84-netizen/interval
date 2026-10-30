@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Android's back preview, played through NavDisplay with a back swipe fed in by hand. */
class BackPreviewTest {
    private val below = Color.Red
    private val top = Color.Blue

    private fun runBackPreviewTest(block: suspend BackPreviewScope.() -> Unit) = runUiTest {
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher()
        }
        val input = DirectNavigationEventInput()
        owner.navigationEventDispatcher.addInput(input)
        val backStack = mutableStateListOf<String>("below", "top")
        setThemedContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                NavDisplay(
                    backStack = backStack,
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), backPreviewCorners()),
                    predictivePopTransitionSpec = backPreviewTransition(),
                    entryProvider = entryProvider {
                        entry("below") { Box(Modifier.fillMaxSize().background(below).testTag("below")) }
                        entry("top") { Box(Modifier.fillMaxSize().background(top).testTag("top")) }
                    },
                )
            }
        }
        BackPreviewScope(this, input, backStack).block()
    }

    private class BackPreviewScope(
        val test: androidx.compose.ui.test.DesktopComposeUiTest,
        val input: DirectNavigationEventInput,
        val backStack: List<String>,
    ) {
        fun swipe(edge: Int, progress: Float) {
            input.backStarted(NavigationEvent(swipeEdge = edge))
            input.backProgressed(NavigationEvent(swipeEdge = edge, progress = progress))
            test.waitForIdle()
        }
    }

    @Test
    fun `a back swipe shrinks the page, rounds its corners and shows the page beneath`() = runBackPreviewTest {
        swipe(NavigationEvent.EDGE_LEFT, progress = 1f)
        val full = Window.Phone.width.toFloat()
        val bounds = test.onNodeWithTag("top").getBoundsInRoot()
        assertTrue(bounds.right - bounds.left < (full * 0.95f).dp, "page shrank: $bounds")
        // Swiped from the left edge, the page drifts right.
        assertTrue((bounds.left + bounds.right) / 2 > (full / 2).dp, "page drifted right: $bounds")
        test.onNodeWithTag("below").assertExists()

        // The leaving page's top-left corner is cut away, showing the page beneath through it.
        val image = test.onRoot().captureToImage().toPixelMap()
        val cornerX = bounds.left.value.toInt() + 1
        val cornerY = bounds.top.value.toInt() + 1
        assertEquals(below, image[cornerX, cornerY], "rounded corner")
        assertEquals(top, image[(bounds.left.value + 40).toInt(), (bounds.top.value + 40).toInt()])
    }

    @Test
    fun `a swipe from the right edge drifts the page left`() = runBackPreviewTest {
        swipe(NavigationEvent.EDGE_RIGHT, progress = 1f)
        val bounds = test.onNodeWithTag("top").getBoundsInRoot()
        assertTrue((bounds.left + bounds.right) / 2 < (Window.Phone.width / 2f).dp, "page drifted left: $bounds")
    }

    @Test
    fun `cancelling the swipe puts the page back square and full size`() = runBackPreviewTest {
        swipe(NavigationEvent.EDGE_LEFT, progress = 0.5f)
        input.backCancelled()
        test.waitForIdle()
        val bounds = test.onNodeWithTag("top").getBoundsInRoot()
        assertEquals(0.dp, bounds.left)
        assertEquals(Window.Phone.width.dp, bounds.right)
        assertEquals(top, test.onRoot().captureToImage().toPixelMap()[1, 1])
        test.onAllNodesWithTag("below").assertCountEquals(0)
        assertEquals(listOf("below", "top"), backStack)
    }

    @Test
    fun `completing the swipe pops the page`() = runBackPreviewTest {
        swipe(NavigationEvent.EDGE_LEFT, progress = 0.5f)
        input.backCompleted()
        test.waitForIdle()
        assertEquals(listOf("below"), backStack)
        test.onAllNodesWithTag("top").assertCountEquals(0)
        assertEquals(below, test.onRoot().captureToImage().toPixelMap()[1, 1])
    }
}
