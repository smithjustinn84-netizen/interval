@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.session

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.waitForText
import dev.justinsmith.lift.ui.liftAppTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Lift on a tablet, end to end: the program families side by side, a session upright in one wider column. Each
 * page's tablet layout over fixed state is tested in its own module (`TabletTest` in lift-today, lift-library
 * and lift-session).
 */
class TabletTest {
    @Test
    fun `Programs lays its families side by side`() = liftAppTest(window = Window.Tablet) {
        waitForText("Follow a program")
        onNodeWithText("Follow a program").performClick()
        waitForText("Getting started")
        val beginner = onNodeWithText("Getting started").assertIsDisplayed().getUnclippedBoundsInRoot()
        val classics = onNodeWithText("Classics").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(classics.left >= beginner.right)
        assertTrue(classics.top == beginner.top)
    }

    @Test
    fun `a session on an upright tablet keeps one wider column`() = liftAppTest(window = Window.TabletPortrait) {
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        waitForText("16 kg × 10")
        val time = onNodeWithText("Time").assertIsDisplayed().getUnclippedBoundsInRoot()
        // Straight after a set, its rest counts down where Since last set stands.
        val since = onNodeWithText("Rest").assertIsDisplayed().getUnclippedBoundsInRoot()
        val set = onNodeWithText("16 kg × 10").assertIsDisplayed().getUnclippedBoundsInRoot()
        val log = onNodeWithText("Log set").assertIsDisplayed().getUnclippedBoundsInRoot()
        // The numbers in one row, the sets under them and Log set at the foot, not a column of each.
        assertTrue(since.top == time.top && since.left > time.right)
        assertTrue(set.top > time.bottom && log.top > set.bottom)
    }
}
