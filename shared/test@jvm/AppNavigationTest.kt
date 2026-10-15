@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.fakes.testKoin
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.sameShape
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import org.koin.core.KoinApplication
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

/** A navigation bar or rail item; "Presets" alone would also match the Timer tab's section heading. */
private fun DesktopComposeUiTest.tab(label: String): SemanticsNodeInteraction =
    onNode(hasText(label) and hasClickAction())

class AppNavigationTest {
    @Test
    fun `the app opens on the timer`() = appTest {
        onNodeWithText("Start workout").assertExists()
        tab("Timer").assertIsSelected()
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
    fun `a preset picked from See all becomes the next workout`() = appTest { koin ->
        onNodeWithText("See all").performClick()
        onNodeWithText("Hangboard").performClick()
        onNodeWithText("Start workout").assertExists()
        tab("Timer").assertIsSelected()
        val config = koin.koin.get<IntervalRepository>().config.value
        assertTrue(config.sameShape(builtInPresets.single { it.name == "Hangboard" }.config))
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
    fun `on a tablet settings opens beside the timer`() = appTest(Window.Tablet) {
        onNodeWithContentDescription("Settings").performClick()
        onNodeWithText("Cues").assertExists()
        onNodeWithText("Start workout").assertExists()

        onNodeWithContentDescription("Close settings").performClick()
        onNodeWithText("Cues").assertDoesNotExist()
    }
}
