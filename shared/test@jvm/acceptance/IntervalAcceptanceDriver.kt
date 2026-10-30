@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.acceptance

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.AppContent
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.fakes.testKoin
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import org.koin.core.KoinApplication

/**
 * Acceptance Test Driver implementing Dave Farley's ATDD pattern for the Interval app.
 *
 * Encapsulates the UI semantics tree behind a ubiquitous domain language, keeping acceptance
 * tests focused on business intent ("What the system does") rather than widget selectors.
 */
@Suppress("detekt.TooManyFunctions")
class IntervalAcceptanceDriver(
    private val ui: DesktopComposeUiTest,
    val koin: KoinApplication,
) {
    val repository: IntervalRepository
        get() = koin.koin.get()

    /** Waits for text to appear in the Compose hierarchy within [timeoutMillis]. */
    fun waitForText(text: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS) {
        ui.waitUntil(timeoutMillis = timeoutMillis) {
            ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Navigates to a top-level tab by its domain name ("Timer", "History", "Lift"). */
    fun navigateToTab(tabName: String) {
        ui.onNode(hasText(tabName) and hasClickAction()).performClick()
    }

    /** Asserts that the specified tab is currently selected in the navigation bar/rail. */
    fun verifyTabSelected(tabName: String) {
        ui.onNode(hasText(tabName) and hasClickAction()).assertIsSelected()
    }

    /** Verifies that the Timer home screen is displayed with its start button. */
    fun verifyTimerScreenShown() {
        verifyTabSelected("Timer")
        ui.onNodeWithText("Start workout").assertExists()
    }

    /** Taps an interval row under Intervals (e.g. "Work", "Rest", "Rounds") to open its editor. */
    fun tapIntervalRow(fieldName: String) {
        ui.onNode(hasText(fieldName) and hasClickAction()).performClick()
    }

    /** Asserts that the ValueSheet for [title] is displayed with quick picks. */
    fun verifyValueSheetShown(title: String) {
        waitForText(title)
        ui.onNodeWithText("Quick picks").assertExists()
    }

    /** Taps a quick-pick preset value within an active value sheet (e.g. "1:00"). */
    fun selectQuickPick(value: String) {
        ui.onNode(hasText(value) and hasClickAction()).performClick()
    }

    /** Dismisses a sheet via the Done button. */
    fun tapDone() {
        ui.onNodeWithText("Done").performClick()
    }

    /** Verifies that an interval row displays the expected formatted value (e.g. "1:00"). */
    fun verifyIntervalValue(fieldName: String, expectedValue: String) {
        ui.onNode(hasText(fieldName) and hasText(expectedValue)).assertExists()
    }

    /** Starts the configured workout from the Timer screen. */
    fun startWorkout() {
        ui.onNodeWithText("Start workout").performClick()
    }

    /** Verifies the currently active workout phase readout (e.g. "Get ready", "Work", "Rest"). */
    fun verifyWorkoutPhase(phaseName: String) {
        waitForText(phaseName)
        ui.onNodeWithText(phaseName).assertExists()
    }

    /** Pauses an active workout using the Pause control. */
    fun pauseWorkout() {
        ui.onNode(hasText("Pause") and hasClickAction()).performClick()
    }

    /** Asserts that the workout is paused, showing the Paused label and Resume button. */
    fun verifyWorkoutPaused() {
        waitForText("Paused")
        ui.onNode(hasText("Resume") and hasClickAction()).assertExists()
    }

    /** Resumes a paused workout using the Resume control. */
    fun resumeWorkout() {
        ui.onNode(hasText("Resume") and hasClickAction()).performClick()
    }

    /** Asserts that the workout is running and can be paused. */
    fun verifyWorkoutRunning() {
        ui.onNode(hasText("Pause") and hasClickAction()).assertExists()
    }

    /** Skips the current interval phase ahead to the next one. */
    fun skipPhase() {
        val skipNode = ui.onAllNodesWithContentDescription("Skip to next interval")
        if (skipNode.fetchSemanticsNodes().isNotEmpty()) {
            skipNode.onLast().performClick()
        } else {
            ui.onNode(hasText("Skip") and hasClickAction()).performClick()
        }
    }

    /** Verifies the workout status readout contains [statusSubstring] (e.g. "Round 1 of 8"). */
    fun verifyWorkoutStatus(statusSubstring: String) {
        ui.onNode(hasText(statusSubstring, substring = true)).assertExists()
    }

    /** Initiates ending the workout from the workout controls. */
    fun endWorkout() {
        val endNode = ui.onAllNodesWithContentDescription("End workout")
        if (endNode.fetchSemanticsNodes().isNotEmpty()) {
            endNode.onLast().performClick()
        } else {
            ui.onNode(hasText("End") and hasClickAction()).performClick()
        }
    }

    /** Verifies that the confirmation dialog for ending the workout is shown with [messageSubstring]. */
    fun verifyEndWorkoutDialog(messageSubstring: String) {
        waitForText("End workout?")
        ui.onNodeWithText("End workout?").assertExists()
        ui.onNode(hasText(messageSubstring, substring = true)).assertExists()
    }

    /** Confirms ending the workout in the confirmation dialog. */
    fun confirmEndWorkout() {
        ui.onAllNodesWithText("End workout").onLast().performClick()
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Executes an acceptance test scenario in [window] over an isolated in-memory Koin container.
 *
 * Provides a clean [IntervalAcceptanceDriver] DSL to author ATDD specifications.
 */
fun acceptanceScenario(
    window: Window = Window.Phone,
    scenario: suspend IntervalAcceptanceDriver.() -> Unit,
) = runUiTest(window) {
    val koin = testKoin()
    try {
        setContent { AppContent(koin) }
        val driver = IntervalAcceptanceDriver(this, koin)
        driver.scenario()
    } finally {
        koin.close()
    }
}
