@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.fakes.testKoin
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.sameShape
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import org.koin.core.KoinApplication
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** The whole app, real ViewModels and repository, over fake platform services. */
private fun appTest(window: Window = Window.Phone, block: suspend DesktopComposeUiTest.(KoinApplication) -> Unit) =
    runUiTest(window) {
        val koin = testKoin()
        try {
            setContent { AppContent(koin) }
            block(koin)
        } finally {
            koin.close()
        }
    }

/** How far, in dp, a moving node may be from its resting place and still count as not moving that way. */
private const val SETTLED_SLACK = 12f

/** A navigation bar or rail item; "History" alone would also match the History screen title. */
private fun DesktopComposeUiTest.tab(label: String): SemanticsNodeInteraction =
    onNode(hasText(label) and hasClickAction())

/** A page's title: the one node with that text and nothing to tap, apart from the bar's item. */
private fun DesktopComposeUiTest.pageTitle(title: String): SemanticsNodeInteraction =
    onNode(hasText(title) and !hasClickAction())

/** The Lift tab's bell, which opens Your equipment. */
private const val EQUIPMENT = "Your equipment and records"

@Suppress("detekt.TooManyFunctions") // One journey per test, side by side.
class AppNavigationTest {
    @Test
    fun `the app opens on the timer`() = appTest {
        onNodeWithText("Start workout").assertExists()
        tab("Timer").assertIsSelected()
    }

    @Test
    fun `the workout rises into place rather than sliding in from the side`() = appTest {
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
        tab("Lift").performClick()
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS / 2L)
        val moving = onNodeWithText("Start session").fetchSemanticsNode().boundsInRoot
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS * 2L)
        val settled = onNodeWithText("Start session").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(moving.left - settled.left) < SETTLED_SLACK, "not sliding sideways: $moving vs $settled")
    }

    @Test
    fun `switching tabs keeps the bar put`() = appTest {
        val before = tab("Lift").fetchSemanticsNode().boundsInRoot
        mainClock.autoAdvance = false
        tab("Lift").performClick()
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS / 2L)
        val during = tab("Lift").fetchSemanticsNode().boundsInRoot
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS * 2L)
        val after = tab("Lift").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(during.top - before.top) < SETTLED_SLACK, "the bar held still: $during vs $before")
        assertTrue(abs(after.top - before.top) < SETTLED_SLACK, "the bar is where it was: $after vs $before")
    }

    @Test
    fun `pushing a page from Lift leaves the tab where it was as it goes`() = appTest {
        tab("Lift").performClick()
        val startBefore = onNodeWithText("Start session").fetchSemanticsNode().boundsInRoot
        val titleBefore = pageTitle("Lift").fetchSemanticsNode().boundsInRoot
        mainClock.autoAdvance = false
        onNodeWithContentDescription(EQUIPMENT).performClick()
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS / 4L)
        // Vertically only: the leaving page drifts sideways by design.
        val start = onNodeWithText("Start session").fetchSemanticsNode().boundsInRoot
        val title = pageTitle("Lift").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(start.bottom - startBefore.bottom) < SETTLED_SLACK, "Start held: $start vs $startBefore")
        assertTrue(abs(title.top - titleBefore.top) < SETTLED_SLACK, "the title held its place: $title vs $titleBefore")
    }

    @Test
    fun `the bar slides away with the push and comes back with the pop`() = appTest {
        tab("Lift").performClick()
        val before = tab("Lift").fetchSemanticsNode().boundsInRoot
        mainClock.autoAdvance = false
        onNodeWithContentDescription(EQUIPMENT).performClick()
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS / 4L)
        val leaving = tab("Lift").fetchSemanticsNode().boundsInRoot
        assertTrue(leaving.top > before.top + SETTLED_SLACK, "sliding down, not gone at once: $leaving vs $before")
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS * 2L)
        tab("Lift").assertDoesNotExist()
        onNodeWithContentDescription("Back").performClick()
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS / 4L)
        val returning = tab("Lift").fetchSemanticsNode().boundsInRoot
        assertTrue(returning.top > before.top + SETTLED_SLACK, "sliding back up: $returning vs $before")
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS * 2L)
        val settled = tab("Lift").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(settled.top - before.top) < SETTLED_SLACK, "back in its place: $settled vs $before")
    }

    @Test
    fun `a back swipe toward Lift brings the bar in with the finger and lets it go again`() = runUiTest(Window.Phone) {
        val koin = testKoin()
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher()
        }
        val input = DirectNavigationEventInput()
        owner.navigationEventDispatcher.addInput(input)
        try {
            setContent {
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) { AppContent(koin) }
            }
            tab("Lift").performClick()
            val before = tab("Lift").fetchSemanticsNode().boundsInRoot
            onNodeWithContentDescription(EQUIPMENT).performClick()
            waitForIdle()
            tab("Lift").assertDoesNotExist()
            input.backStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT))
            input.backProgressed(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0.5f))
            waitForIdle()
            val halfway = tab("Lift").fetchSemanticsNode().boundsInRoot
            assertTrue(halfway.top > before.top + SETTLED_SLACK, "on its way in, not in place: $halfway vs $before")
            input.backCancelled()
            waitForIdle()
            tab("Lift").assertDoesNotExist()
            input.backStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT))
            input.backProgressed(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0.5f))
            input.backCompleted()
            waitForIdle()
            val settled = tab("Lift").fetchSemanticsNode().boundsInRoot
            assertTrue(abs(settled.top - before.top) < SETTLED_SLACK, "back in its place: $settled vs $before")
        } finally {
            koin.close()
        }
    }

    @Test
    fun `the tabs switch screens`() = appTest {
        tab("Lift").performClick()
        onNodeWithText("Start session").assertExists()
        tab("Lift").assertIsSelected()

        tab("History").performClick()
        onNodeWithText("Train to a plan").assertExists()

        tab("Timer").performClick()
        onNodeWithText("Start workout").assertExists()
    }

    @Test
    fun `a preset put on the Timer from All presets becomes the next workout`() = appTest { koin ->
        onNodeWithContentDescription("All presets").performClick()
        onNodeWithText("Hangboard").performClick()
        onNodeWithText("Put on Timer").performClick()
        onNodeWithText("Start workout").assertExists()
        tab("Timer").assertIsSelected()
        val config = koin.koin.get<IntervalRepository>().config.value
        assertTrue(config.sameShape(builtInPresets.single { it.name == "Hangboard" }.config))
    }

    @Test
    fun `a preset started from its sheet opens the workout and ends back on Presets`() = appTest { koin ->
        onNodeWithContentDescription("All presets").performClick()
        onNodeWithText("Tabata").performClick()
        onNodeWithText("Start workout").performClick()
        onNodeWithContentDescription("End workout").assertExists()
        assertTrue(koin.koin.get<IntervalRepository>().config.value.sameShape(builtInPresets.first().config))

        onNodeWithContentDescription("End workout").performClick()
        onNodeWithText("End workout").performClick()
        onNodeWithText("Built-in").assertExists()
        onNodeWithText("Put on Timer").assertDoesNotExist()
        // Presets is a page over the Timer, so Back returns there.
        tab("Timer").assertDoesNotExist()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Start workout").assertExists()
    }

    @Test
    fun `a preset saved on the Presets page is named on the Timer`() = appTest {
        onNodeWithContentDescription("All presets").performClick()
        onNodeWithContentDescription("New preset").performClick()
        // The unsaved card's own Save pill stays in the tree under the editor, drawn first.
        onAllNodesWithText("Save").onLast().performClick()
        onNodeWithText("30/15 ×8").assertExists()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("30/15 ×8 · Total").assertExists()
    }

    @Test
    fun `a workout opens full screen and ends back on the timer`() = appTest {
        onNodeWithText("Start workout").performClick()
        onNodeWithContentDescription("End workout").assertExists()
        tab("Timer").assertDoesNotExist()

        onNodeWithContentDescription("End workout").performClick()
        onNodeWithText("End workout").performClick()
        onNodeWithText("Start workout").assertExists()
    }

    @Test
    fun `on a phone settings opens full screen and goes back`() = appTest {
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
        onNodeWithText("About Interval").performScrollTo().performClick()
        onNodeWithText("Version 1.2.3").assertExists()
        onNodeWithText("Cues").assertDoesNotExist()

        onNodeWithText("Open-source licenses").performClick()
        onNodeWithText("Barlow").assertExists()

        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Version 1.2.3").assertExists()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Cues").assertExists()
    }

    @Test
    fun `on a tablet settings opens beside the timer`() = appTest(Window.Tablet) {
        onNodeWithContentDescription("Settings").performClick()
        onNodeWithText("Cues").assertExists()
        onNodeWithText("Start workout").assertExists()

        onNodeWithContentDescription("Close settings").performClick()
        onNodeWithText("Cues").assertDoesNotExist()
    }

    @Test
    fun `on a tablet a finished workout opens beside History and closing it leaves the prompt`() =
        appTest(Window.Tablet) { koin ->
            koin.koin.get<IntervalRepository>().recordWorkout(builtInPresets.first().config, elapsedSeconds = 240)
            tab("History").performClick()
            onNodeWithText("Pick a workout").assertExists()

            onNode(hasText("Tabata") and hasClickAction()).performClick()
            onNodeWithText("Repeat now").assertExists()
            onNodeWithText("Week").assertExists()
            onNodeWithText("Pick a workout").assertDoesNotExist()
            tab("History").assertIsSelected()
            onNode(hasText("Tabata") and hasClickAction()).assertIsSelected()
            onNodeWithContentDescription("Back").assertDoesNotExist()

            onNodeWithContentDescription("Close workout").performClick()
            onNodeWithText("Pick a workout").assertExists()
            onNodeWithText("Week").assertExists()
        }

    @Test
    fun `on a tablet another workout takes the open one's place`() = appTest(Window.Tablet) { koin ->
        val (tabata, hiit) = builtInPresets
        val repository = koin.koin.get<IntervalRepository>()
        repository.recordWorkout(tabata.config, elapsedSeconds = 240)
        repository.recordWorkout(hiit.config, elapsedSeconds = 600)
        tab("History").performClick()
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
    fun `on a tablet deleting the workout beside History leaves the prompt`() = appTest(Window.Tablet) { koin ->
        koin.koin.get<IntervalRepository>().recordWorkout(builtInPresets.first().config, elapsedSeconds = 240)
        tab("History").performClick()
        onNode(hasText("Tabata") and hasClickAction()).performClick()
        // History's own menu, then the workout's beside it.
        onAllNodesWithContentDescription("More options").onLast().performClick()
        onNodeWithText("Delete workout").performClick()
        onNodeWithText("Delete").performClick()
        onNodeWithText("Train to a plan").assertExists()
        onNodeWithText("Pick a workout").assertExists()
        tab("History").assertIsSelected()
    }

    @Test
    fun `beside the timer settings slides in from the end and slides back out`() = appTest(Window.Tablet) {
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
    fun `the desktop window opens settings beside the timer`() = appTest(Window.Desktop) {
        onNodeWithContentDescription("Settings").performClick()
        onNodeWithText("Cues").assertExists()
        onNodeWithText("Start workout").assertExists()
    }

    @Test
    fun `on a tablet held upright settings opens full screen`() = appTest(Window.TabletPortrait) {
        onNodeWithContentDescription("Settings").performClick()
        onNodeWithText("Cues").assertExists()
        onNodeWithText("Start workout").assertDoesNotExist()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Start workout").assertExists()
    }

    @Test
    fun `a finished workout opens from History and deleting it goes back`() = appTest { koin ->
        val tabata = builtInPresets.first()
        koin.koin.get<IntervalRepository>().recordWorkout(tabata.config, elapsedSeconds = 240)
        tab("History").performClick()
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
    fun `deleting a workout says so and Undo brings it back`() = appTest { koin ->
        koin.koin.get<IntervalRepository>().recordWorkout(builtInPresets.first().config, elapsedSeconds = 240)
        tab("History").performClick()
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
        onNodeWithContentDescription("All presets").performClick()
        onNodeWithContentDescription("New preset").performClick()
        onAllNodesWithText("Save").onLast().performClick()
        onNodeWithText("Saved as 30/15 ×8").assertExists()
        onNodeWithText("30/15 ×8").performClick()
        onNodeWithText("Delete").performClick()
        onAllNodesWithText("Delete").onLast().performClick()
        onNodeWithText("Deleted 30/15 ×8").assertExists()
        onNodeWithText("Nothing saved yet").assertExists()
        onNodeWithText("Undo").performClick()
        onNodeWithText("30/15 ×8").assertExists()
    }

    @Test
    fun `a plan taken off a day comes back with Undo`() = appTest { koin ->
        koin.koin.get<IntervalRepository>().planWeekly(builtInPresets.first(), setOf(1))
        tab("History").performClick()
        onNodeWithContentDescription("Plan").performClick()
        onNodeWithContentDescription("Remove Tabata from Monday").performClick()
        onNodeWithContentDescription("Remove Tabata from Monday").assertDoesNotExist()
        onNodeWithText("Removed Tabata from Monday").assertExists()
        onNodeWithText("Undo").performClick()
        onNodeWithContentDescription("Remove Tabata from Monday").assertExists()
    }

    @Test
    fun `the plan opens from History and goes back`() = appTest {
        tab("History").performClick()
        onNodeWithContentDescription("Plan").performClick()
        onNodeWithText("Weekly goal").assertExists()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Train to a plan").assertExists()
    }
}
