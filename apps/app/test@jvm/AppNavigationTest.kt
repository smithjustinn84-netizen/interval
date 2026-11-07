@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.navigationevent.NavigationEvent
import dev.justinsmith.interval.model.asPlan
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.sameShape
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.onMainThread
import dev.justinsmith.interval.ui.performClick
import dev.justinsmith.interval.ui.scrollToText
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** How far, in dp, a moving node may be from its resting place and still count as not moving that way. */
private const val SETTLED_SLACK = 12f

/** A page's title: the one node with that text and nothing to tap, apart from the bar's item. */
private fun DesktopComposeUiTest.pageTitle(title: String): SemanticsNodeInteraction =
    onNode(hasText(title) and !hasClickAction())

/** The Lift tab's bell, which opens Your equipment. */
// A page pushed from Lift's title bar: a new program's editor.
private const val NEW_PROGRAM = "New program"

@Suppress("detekt.TooManyFunctions") // One journey per test, side by side.
class AppNavigationTest {
    @Test
    fun `the app opens on today`() = appTest {
        onNodeWithText("This week").assertExists()
        tab("Today").assertIsSelected()
    }

    @Test
    fun `the workout rises into place rather than sliding in from the side`() = appTest {
        openTimer()
        waitForIdle()
        mainClock.autoAdvance = false
        onNodeWithText("Start workout").performClick()
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS / 5L)
        val moving = onNodeWithContentDescription("End workout").fetchSemanticsNode().boundsInRoot
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS * 2L)
        val settled = onNodeWithContentDescription("End workout").fetchSemanticsNode().boundsInRoot
        assertTrue(moving.top > settled.top + SETTLED_SLACK, "still below its place: $moving vs $settled")
        assertTrue(abs(moving.left - settled.left) < SETTLED_SLACK, "not sliding sideways: $moving vs $settled")
    }

    @Test
    fun `switching tabs fades through rather than sliding`() = appTest {
        mainClock.autoAdvance = false
        tab("Train").performClick()
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS / 2L)
        val moving = onNodeWithText("Start session").fetchSemanticsNode().boundsInRoot
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS * 2L)
        val settled = onNodeWithText("Start session").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(moving.left - settled.left) < SETTLED_SLACK, "not sliding sideways: $moving vs $settled")
    }

    @Test
    fun `switching tabs keeps the bar put`() = appTest {
        val before = tab("Train").fetchSemanticsNode().boundsInRoot
        mainClock.autoAdvance = false
        tab("Train").performClick()
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS / 2L)
        val during = tab("Train").fetchSemanticsNode().boundsInRoot
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS * 2L)
        val after = tab("Train").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(during.top - before.top) < SETTLED_SLACK, "the bar held still: $during vs $before")
        assertTrue(abs(after.top - before.top) < SETTLED_SLACK, "the bar is where it was: $after vs $before")
    }

    @Test
    fun `pushing a page from Lift leaves the tab where it was as it goes`() = appTest {
        tab("Train").performClick()
        val startBefore = onNodeWithText("Start session").fetchSemanticsNode().boundsInRoot
        val titleBefore = pageTitle("Train").fetchSemanticsNode().boundsInRoot
        mainClock.autoAdvance = false
        onNodeWithContentDescription(NEW_PROGRAM).performClick()
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS / 4L)
        // Vertically only: the leaving page drifts sideways by design.
        val start = onNodeWithText("Start session").fetchSemanticsNode().boundsInRoot
        val title = pageTitle("Train").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(start.bottom - startBefore.bottom) < SETTLED_SLACK, "Start held: $start vs $startBefore")
        assertTrue(abs(title.top - titleBefore.top) < SETTLED_SLACK, "the title held its place: $title vs $titleBefore")
    }

    @Test
    fun `the bar slides away with the push and comes back with the pop`() = appTest {
        tab("Train").performClick()
        val before = tab("Train").fetchSemanticsNode().boundsInRoot
        mainClock.autoAdvance = false
        onNodeWithContentDescription(NEW_PROGRAM).performClick()
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS / 4L)
        val leaving = tab("Train").fetchSemanticsNode().boundsInRoot
        assertTrue(leaving.top > before.top + SETTLED_SLACK, "sliding down, not gone at once: $leaving vs $before")
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS * 2L)
        tab("Train").assertDoesNotExist()
        onNodeWithContentDescription("Back").performClick()
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS / 4L)
        val returning = tab("Train").fetchSemanticsNode().boundsInRoot
        assertTrue(returning.top > before.top + SETTLED_SLACK, "sliding back up: $returning vs $before")
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS * 2L)
        val settled = tab("Train").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(settled.top - before.top) < SETTLED_SLACK, "back in its place: $settled vs $before")
    }

    @Test
    fun `a back swipe toward Lift brings the bar in with the finger and lets it go again`() = appTest { app ->
        val input = app.backInput
        tab("Train").performClick()
        val before = tab("Train").fetchSemanticsNode().boundsInRoot
        onNodeWithContentDescription(NEW_PROGRAM).performClick()
        waitForIdle()
        tab("Train").assertDoesNotExist()
        onMainThread {
            input.backStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT))
            input.backProgressed(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0.5f))
        }
        waitForIdle()
        val halfway = tab("Train").fetchSemanticsNode().boundsInRoot
        assertTrue(halfway.top > before.top + SETTLED_SLACK, "on its way in, not in place: $halfway vs $before")
        onMainThread { input.backCancelled() }
        waitForIdle()
        tab("Train").assertDoesNotExist()
        onMainThread {
            input.backStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT))
            input.backProgressed(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0.5f))
            input.backCompleted()
        }
        waitForIdle()
        val settled = tab("Train").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(settled.top - before.top) < SETTLED_SLACK, "back in its place: $settled vs $before")
    }

    @Test
    fun `switching Train's pillars swaps the cards under a title that stays where it is`() = appTest {
        tab("Train").performClick()
        waitForIdle()
        val before = pageTitle("Train").fetchSemanticsNode().boundsInRoot
        mainClock.autoAdvance = false
        onNode(hasText("Conditioning") and isSelectable()).performClick()
        mainClock.advanceTimeBy(Motion.EXIT_MILLIS / 2L)
        // Mid-swap both pillars are drawn, each with its title where the other's is.
        val titles = onAllNodes(hasText("Train") and !hasClickAction()).fetchSemanticsNodes()
        assertTrue(titles.size == 2, "both pillars on screen mid-swap: ${titles.size}")
        titles.forEach { title ->
            assertTrue(title.boundsInRoot == before, "the title held its place: ${title.boundsInRoot} vs $before")
        }
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS * 2L)
        onNodeWithText("Built-in").assertExists()
        onNodeWithText("Start session").assertDoesNotExist()
        tab("Train").assertIsSelected()
    }

    @Test
    fun `the tabs switch screens`() = appTest {
        tab("Train").performClick()
        onNodeWithText("Start session").assertExists()
        tab("Train").assertIsSelected()

        tab("Log").performClick()
        onNodeWithText("Train to a plan").assertExists()

        // The Timer is a page over Train's Conditioning pillar: the bar is away until Back.
        openTimer()
        onNodeWithText("Start workout").assertExists()
        tab("Train").assertDoesNotExist()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Built-in").assertExists()
        tab("Train").assertIsSelected()

        tab("Today").performClick()
        onNodeWithText("This week").assertExists()
        tab("Today").assertIsSelected()
    }

    @Test
    fun `a preset put on the Timer from All presets becomes the next workout`() = appTest { app ->
        openTimer()
        // The Timer's way to the presets is the way back to them.
        onNodeWithContentDescription("All presets").performClick()
        scrollToText("Hangboard")
        onNodeWithText("Hangboard").performClick()
        onNodeWithText("Put on Timer").performClick()
        onNodeWithText("Start workout").assertExists()
        onNodeWithText("Hangboard · Total").assertExists()
        val config = app.repository.settings.config.value
        assertTrue(config.sameShape(builtInPresets.single { it.name == "Hangboard" }.config))
    }

    @Test
    fun `a preset started from its sheet opens the workout and ends back on the presets`() = appTest { app ->
        tab("Train").performClick()
        onNode(hasText("Conditioning") and isSelectable()).performClick()
        onNodeWithText("Tabata").performClick()
        onNodeWithText("Start workout").performClick()
        onNodeWithContentDescription("End workout").assertExists()
        assertTrue(app.repository.settings.config.value.sameShape(builtInPresets.first().config))

        onNodeWithContentDescription("End workout").performClick()
        onNodeWithText("End workout").performClick()
        onNodeWithText("Built-in").assertExists()
        onNodeWithText("Put on Timer").assertDoesNotExist()
        // The presets are Train's Conditioning pillar, a tab: the bar is back.
        tab("Train").assertIsSelected()
    }

    @Test
    fun `a preset saved among the presets is named on the Timer`() = appTest {
        tab("Train").performClick()
        onNode(hasText("Conditioning") and isSelectable()).performClick()
        onNodeWithContentDescription("New preset").performClick()
        // The unsaved card's own Save pill stays in the tree under the editor, drawn first.
        onAllNodesWithText("Save").onLast().performScrollTo().performClick()
        onNodeWithText("30/15 ×8").assertExists()
        onNode(hasText("Timer") and hasClickAction()).performClick()
        onNodeWithText("30/15 ×8 · Total").assertExists()
    }

    @Test
    fun `a workout opens full screen and ends back on the timer`() = appTest {
        openTimer()
        onNodeWithText("Start workout").performClick()
        onNodeWithContentDescription("End workout").assertExists()
        tab("Train").assertDoesNotExist()

        onNodeWithContentDescription("End workout").performClick()
        onNodeWithText("End workout").performClick()
        onNodeWithText("Start workout").assertExists()
    }

    @Test
    fun `on a phone settings opens full screen and goes back`() = appTest {
        openTimer()
        onNodeWithContentDescription("Settings").performClick()
        onNodeWithText("Cues").assertExists()
        onNodeWithText("Start workout").assertDoesNotExist()

        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Start workout").assertExists()
        onNodeWithText("Cues").assertDoesNotExist()
    }

    @Test
    fun `settings leads to about, about to the licenses, and back again`() = appTest {
        onNodeWithContentDescription("Settings").performClick()
        scrollToText("About Interval")
        onNodeWithText("About Interval").performClick()
        onNodeWithText("Version 1.2.3").assertExists()
        onNodeWithText("Cues").assertDoesNotExist()

        onNodeWithText("Open-source licenses").performClick()
        onNodeWithText("Barlow").assertExists()

        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Version 1.2.3").assertExists()
        onNodeWithContentDescription("Back").performClick()
        // Back on Settings where it was left, scrolled down to About.
        onNodeWithText("About Interval").assertExists()
    }

    @Test
    fun `on a tablet settings opens beside the timer`() = appTest(Window.Tablet) {
        openTimer()
        onNodeWithContentDescription("Settings").performClick()
        onNodeWithText("Cues").assertExists()
        onNodeWithText("Start workout").assertExists()

        onNodeWithContentDescription("Close settings").performClick()
        onNodeWithText("Cues").assertDoesNotExist()
    }

    @Test
    fun `on a tablet a finished workout opens beside History and closing it leaves the prompt`() =
        appTest(Window.Tablet) { app ->
            app.repository.history.recordWorkout(builtInPresets.first().config, elapsedSeconds = 240)
            tab("Log").performClick()
            onNodeWithText("Pick a workout").assertExists()

            onNode(hasText("Tabata") and hasClickAction()).performClick()
            onNodeWithText("Repeat now").assertExists()
            onNodeWithText("Week").assertExists()
            onNodeWithText("Pick a workout").assertDoesNotExist()
            tab("Log").assertIsSelected()
            onNode(hasText("Tabata") and hasClickAction()).assertIsSelected()
            onNodeWithContentDescription("Back").assertDoesNotExist()

            onNodeWithContentDescription("Close workout").performClick()
            onNodeWithText("Pick a workout").assertExists()
            onNodeWithText("Week").assertExists()
        }

    @Test
    fun `on a tablet another workout takes the open one's place`() = appTest(Window.Tablet) { app ->
        val (tabata, hiit) = builtInPresets
        val repository = app.repository
        repository.history.recordWorkout(tabata.config, elapsedSeconds = 240)
        repository.history.recordWorkout(hiit.config, elapsedSeconds = 600)
        tab("Log").performClick()
        onNode(hasText(tabata.name) and hasClickAction()).performClick()
        onNode(hasText(hiit.name) and hasClickAction()).performClick()
        // The row, and the page beside it: the Tabata page has gone.
        onAllNodesWithText(hiit.name).assertCountEquals(2)
        onAllNodesWithText(tabata.name).assertCountEquals(1)

        // One close, and History waits for a pick again rather than showing Tabata.
        onNodeWithContentDescription("Close workout").performClick()
        onNodeWithText("Pick a workout").assertExists()
        onAllNodesWithText(tabata.name).assertCountEquals(1)
    }

    @Test
    fun `on a tablet deleting the workout beside History leaves the prompt`() = appTest(Window.Tablet) { app ->
        app.repository.history.recordWorkout(builtInPresets.first().config, elapsedSeconds = 240)
        tab("Log").performClick()
        onNode(hasText("Tabata") and hasClickAction()).performClick()
        // History's own menu, then the workout's beside it.
        onAllNodesWithContentDescription("More options").onLast().performClick()
        onNodeWithText("Delete workout").performClick()
        onNodeWithText("Delete").performClick()
        onNodeWithText("Train to a plan").assertExists()
        onNodeWithText("Pick a workout").assertExists()
        tab("Log").assertIsSelected()
    }

    @Test
    fun `beside the timer settings slides in from the end and slides back out`() = appTest(Window.Tablet) {
        openTimer()
        waitForIdle()
        mainClock.autoAdvance = false
        onNodeWithContentDescription("Settings").performClick()
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS / 5L)
        val arriving = onNodeWithText("Cues").fetchSemanticsNode().boundsInRoot
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS * 3L)
        val settled = onNodeWithText("Cues").fetchSemanticsNode().boundsInRoot
        assertTrue(arriving.left > settled.left + SETTLED_SLACK, "still sliding in: $arriving vs $settled")
        onNodeWithText("Start workout").assertExists()

        onNodeWithContentDescription("Close settings").performClick()
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS / 5L)
        val leaving = onNodeWithText("Cues").fetchSemanticsNode().boundsInRoot
        assertTrue(leaving.left > settled.left + SETTLED_SLACK, "sliding out, not gone at once: $leaving vs $settled")
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS * 3L)
        onNodeWithText("Cues").assertDoesNotExist()
    }

    @Test
    fun `on a tablet settings opened from Today is a page of its own`() = appTest(Window.Tablet) {
        onNodeWithContentDescription("Settings").performClick()
        onNodeWithText("Cues").assertExists()
        onNodeWithText("This week").assertDoesNotExist()
        onNodeWithContentDescription("Close settings").assertDoesNotExist()

        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("This week").assertExists()
        tab("Today").assertIsSelected()
    }

    @Test
    fun `on a tablet settings opened from Lift is a page of its own`() = appTest(Window.Tablet) {
        tab("Train").performClick()
        onNodeWithContentDescription("Settings").performClick()
        onNodeWithText("Cues").assertExists()
        onNodeWithText("Start session").assertDoesNotExist()
        onNodeWithContentDescription("Close settings").assertDoesNotExist()

        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Start session").assertExists()
        tab("Train").assertIsSelected()
    }

    @Test
    fun `on a tablet settings opened from History is a page of its own`() = appTest(Window.Tablet) {
        tab("Log").performClick()
        onNodeWithContentDescription("Settings").performClick()
        onNodeWithText("Cues").assertExists()
        onNodeWithContentDescription("Close settings").assertDoesNotExist()

        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Cues").assertDoesNotExist()
        tab("Log").assertIsSelected()
    }

    @Test
    fun `the desktop window opens settings beside the timer`() = appTest(Window.Desktop) {
        openTimer()
        onNodeWithContentDescription("Settings").performClick()
        onNodeWithText("Cues").assertExists()
        onNodeWithText("Start workout").assertExists()
    }

    @Test
    fun `on a tablet held upright settings opens full screen`() = appTest(Window.TabletPortrait) {
        openTimer()
        onNodeWithContentDescription("Settings").performClick()
        onNodeWithText("Cues").assertExists()
        onNodeWithText("Start workout").assertDoesNotExist()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Start workout").assertExists()
    }

    @Test
    fun `a finished workout opens from History and deleting it goes back`() = appTest { app ->
        val tabata = builtInPresets.first()
        app.repository.history.recordWorkout(tabata.config, elapsedSeconds = 240)
        tab("Log").performClick()
        onNodeWithText("Tabata").performClick()
        onNodeWithText("Repeat now").assertExists()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Week").assertExists()

        onNodeWithText("Tabata").performClick()
        onNodeWithContentDescription("More options").performClick()
        onNodeWithText("Delete workout").performClick()
        onNodeWithText("Delete").performClick()
        onNodeWithText("Train to a plan").assertExists()
    }

    @Test
    fun `deleting a workout says so and Undo brings it back`() = appTest { app ->
        app.repository.history.recordWorkout(builtInPresets.first().config, elapsedSeconds = 240)
        tab("Log").performClick()
        onNodeWithText("Tabata").performClick()
        onNodeWithContentDescription("More options").performClick()
        onNodeWithText("Delete workout").performClick()
        onNodeWithText("Delete").performClick()
        onNodeWithText("Train to a plan").assertExists()
        onNodeWithText("Workout deleted").assertExists()
        onNodeWithText("Undo").performClick()
        onNodeWithText("Tabata").assertExists()
        onNodeWithText("Workout deleted").assertDoesNotExist()
    }

    @Test
    fun `a saved preset says its name and a deleted one comes back with Undo`() = appTest {
        tab("Train").performClick()
        onNode(hasText("Conditioning") and isSelectable()).performClick()
        onNodeWithContentDescription("New preset").performClick()
        onAllNodesWithText("Save").onLast().performScrollTo().performClick()
        onNodeWithText("Saved as 30/15 ×8").assertExists()
        onNodeWithText("30/15 ×8").performClick()
        onNodeWithText("Delete").performClick()
        onAllNodesWithText("Delete").onLast().performClick()
        onNodeWithText("Deleted 30/15 ×8").assertExists()
        onNodeWithText("Yours").assertDoesNotExist()
        onNodeWithText("Undo").performClick()
        onNodeWithText("30/15 ×8").assertExists()
    }

    @Test
    fun `a plan taken off a day comes back with Undo`() = appTest { app ->
        app.repository.plans.planWeekly(builtInPresets.first().asPlan(), setOf(1))
        tab("Log").performClick()
        onNodeWithText("Edit plan").performClick()
        onNodeWithContentDescription("Remove Tabata from Monday").performClick()
        onNodeWithContentDescription("Remove Tabata from Monday").assertDoesNotExist()
        onNodeWithText("Removed Tabata from Monday").assertExists()
        onNodeWithText("Undo").performClick()
        onNodeWithContentDescription("Remove Tabata from Monday").assertExists()
    }

    @Test
    fun `the plan opens from History and goes back`() = appTest {
        tab("Log").performClick()
        onNodeWithText("Plan your week").performClick()
        onNodeWithText("Weekly goal").assertExists()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Train to a plan").assertExists()
    }
}
