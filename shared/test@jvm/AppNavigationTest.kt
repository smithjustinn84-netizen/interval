@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** A navigation bar or rail item; "Presets" alone would also match the Presets screen title. */
private fun DesktopComposeUiTest.tab(label: String): SemanticsNodeInteraction =
    onNode(hasText(label) and hasClickAction())

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
        tab("Presets").performClick()
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS / 2L)
        val moving = onNodeWithText("Built-in").fetchSemanticsNode().boundsInRoot
        mainClock.advanceTimeBy(Motion.PAGE_MILLIS * 2L)
        val settled = onNodeWithText("Built-in").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(moving.left - settled.left) < SETTLED_SLACK, "not sliding sideways: $moving vs $settled")
    }

    @Test
    fun `the tabs switch screens`() = appTest {
        tab("Presets").performClick()
        onNodeWithText("Built-in").assertExists()
        tab("Presets").assertIsSelected()

        tab("History").performClick()
        onNodeWithText("Finished workouts show up here.").assertExists()

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
        tab("Presets").performClick()
        onNodeWithText("Tabata").performClick()
        onNodeWithText("Start workout").performClick()
        onNodeWithContentDescription("End workout").assertExists()
        assertTrue(koin.koin.get<IntervalRepository>().config.value.sameShape(builtInPresets.first().config))

        onNodeWithContentDescription("End workout").performClick()
        onNodeWithText("End workout").performClick()
        onNodeWithText("Built-in").assertExists()
        tab("Presets").assertIsSelected()
        onNodeWithText("Put on Timer").assertDoesNotExist()
    }

    @Test
    fun `a preset saved on the Presets tab is named on the Timer`() = appTest {
        tab("Presets").performClick()
        onNodeWithContentDescription("New preset").performClick()
        // The unsaved card's own Save pill stays in the tree under the editor, drawn first.
        onAllNodesWithText("Save").onLast().performClick()
        onNodeWithText("30/15 ×8").assertExists()
        tab("Timer").performClick()
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
        onNodeWithText("About Interval").performClick()
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
}
