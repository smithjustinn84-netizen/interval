@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.fakes.FakeLiftStorage
import dev.justinsmith.interval.fakes.testKoin
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.ui.runUiTest
import org.koin.core.KoinApplication
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertTrue

/** What a Lift test drives: the container, Lift's storage under it, and the clock. */
private class LiftApp(val koin: KoinApplication, val lift: FakeLiftStorage, val clock: AtomicLong)

/** The whole app over fakes, Lift's storage and the clock in the test's hands. */
private fun liftTest(lift: FakeLiftStorage = FakeLiftStorage(), block: suspend DesktopComposeUiTest.(LiftApp) -> Unit) =
    runUiTest {
        val clock = AtomicLong(0L)
        val koin = testKoin(lift = lift, clock = WallClock { clock.get() })
        try {
            setContent { AppContent(koin) }
            block(LiftApp(koin, lift, clock))
        } finally {
            koin.close()
        }
    }

/** A navigation bar or rail item, rather than a page title of the same word. */
private fun DesktopComposeUiTest.tab(label: String): SemanticsNodeInteraction =
    onNode(hasText(label) and hasClickAction())

/** How long Lift's view models, ticking on Swing's thread, may take to show what the test did. */
private const val STATE_TIMEOUT_MILLIS = 5_000L

/** Waits for [text]: the session's view models emit on `Dispatchers.Main`, which the test doesn't track. */
private fun DesktopComposeUiTest.waitForText(text: String) =
    waitUntil(timeoutMillis = STATE_TIMEOUT_MILLIS) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

class LiftFlowTest {
    @Test
    fun `a session logged on Lift lands in History and the week`() = liftTest { app ->
        tab("Lift").performClick()
        onNodeWithText("Start session").performClick()
        waitForText("Log set")
        onNodeWithText("Log set").performClick()
        // The sheet's own Log set, over the screen's.
        onAllNodesWithText("Log set").onLast().performClick()
        waitForText("16 kg × 10")
        app.clock.set(90_000)
        onNodeWithText("Finish").performClick()
        waitForText("New records")
        onAllNodesWithText("Done").onLast().performClick()
        waitForText("Last session")

        tab("History").performClick()
        val row = hasText("Two-hand swing") and hasClickAction()
        waitUntil(timeoutMillis = STATE_TIMEOUT_MILLIS) { onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasText("Kettlebell", substring = true) and hasText("at 90000", substring = true)).assertExists()
        // History's week counts it too.
        onNodeWithText("1 workout").assertExists()
    }

    @Test
    fun `the goal set on Plan shows on Lift`() = liftTest { app ->
        app.koin.koin.get<IntervalRepository>().updateAppSettings { it.copy(weeklyGoal = 3) }
        tab("Lift").performClick()
        waitForText("3 more to hit 3")
    }

    @Test
    fun `a session opens from History and Delete takes it out`() = liftTest(
        FakeLiftStorage().apply { finished("b1", "Swings", startedAt = 0, finishedAt = 600_000, 24.0 to 10) },
    ) { app ->
        tab("History").performClick()
        onNode(hasText("Swings") and hasClickAction()).performClick()
        waitForText("Two-hand swing")
        onNodeWithText("24 kg × 10").assertExists()
        onNodeWithText("Delete").performClick()
        onNodeWithText("Delete session?").assertExists()
        onAllNodesWithText("Delete").onLast().performClick()
        waitForText("Train to a plan")
        onNode(hasText("Swings") and hasClickAction()).assertDoesNotExist()
        assertTrue(app.lift.sessions.sessions.value.single().deleted)
    }

    @Test
    fun `Lift opens the bells owned and Back returns`() = liftTest {
        tab("Lift").performClick()
        onNodeWithContentDescription("Your bells and records").performClick()
        onNodeWithText("Your bells").assertExists()
        tab("Lift").assertDoesNotExist()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Start session").assertExists()
    }
}
