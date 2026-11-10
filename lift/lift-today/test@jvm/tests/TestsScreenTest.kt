@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.tests

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.interval.ui.waitForText
import dev.justinsmith.lift.domain.Attempt
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.Retest
import dev.justinsmith.lift.domain.TestId
import dev.justinsmith.lift.domain.strengthTests
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Tests page over fixed state. What it does over the real stores is in lift-nav's `LiftTestsAcceptanceTest`. */
class TestsScreenTest {
    /** The snatch test done twice at 24 kg and once at 16, the last three weeks back; the plank never. */
    private fun state(taxed: Boolean = false, inProgress: Boolean = false) = TestsUiState(
        tests = strengthTests.map { test ->
            if (test.id == TestId.SnatchTest) {
                TestRow(
                    test,
                    listOf(
                        Attempt("a", 0, Load.Bell(24.0), 70),
                        Attempt("b", 1, Load.Bell(16.0), 120),
                        Attempt("c", 2, Load.Bell(24.0), 88),
                    ),
                    Retest.Resting,
                    daysSince = 21,
                    bells = listOf(16.0, 24.0),
                    bellKg = 24.0,
                )
            } else {
                TestRow(test)
            }
        },
        taxed = taxed,
        inProgress = inProgress,
    )

    @Test
    fun `a test's sheet shows the attempts at the bell picked and starts it there`() = runUiTest {
        val started = mutableListOf<Pair<TestId, Double?>>()
        setThemedContent(dark = true) {
            TestsScreen(state(taxed = true), onStart = { id, kg -> started += id to kg }, onBack = {})
        }
        onNodeWithText("88 reps with 24 kg · 3 weeks ago").assertExists()
        onNodeWithText("Snatch test").performClick()
        waitForText("Best 88 reps · last 88 reps")
        onNodeWithText("Last tested 3 weeks ago.").assertExists()
        onNodeWithText("You are carrying a hard day. A test today will not show your best.").assertExists()
        onNodeWithText("16 kg").performClick()
        waitForText("Best 120 reps · last 120 reps")
        onNodeWithText("Start the test").performClick()
        assertEquals(listOf<Pair<TestId, Double?>>(TestId.SnatchTest to 16.0), started)
    }

    @Test
    fun `a test with no bell asks for none and one can't start over a session in progress`() = runUiTest {
        setThemedContent(dark = true) {
            TestsScreen(state(inProgress = true), onStart = { _, _ -> }, onBack = {}, open = TestId.PlankHold)
        }
        waitForText("None yet: the first sets the mark.")
        onNodeWithText("Bell").assertDoesNotExist()
        onNodeWithText("Finish the session in progress first.").assertExists()
        onNodeWithText("Start the test").assertIsNotEnabled()
    }
}
