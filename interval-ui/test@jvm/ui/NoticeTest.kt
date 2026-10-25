@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals

class NoticeTest {
    @Test
    fun `a notice shows its text and goes by itself`() = runUiTest {
        val notices = Notices()
        setThemedContent { Box { NoticeHost(notices) } }
        mainClock.autoAdvance = false
        notices.show("Saved as Tabata")
        mainClock.advanceTimeBy(Motion.ENTER_MILLIS.toLong())
        onNodeWithText("Saved as Tabata").assertIsDisplayed()
        mainClock.advanceTimeBy(Motion.NOTICE_MILLIS + Motion.EXIT_MILLIS)
        onNodeWithText("Saved as Tabata").assertDoesNotExist()
    }

    @Test
    fun `undo runs its action and takes the notice down`() = runUiTest {
        val notices = Notices()
        var undone = 0
        setThemedContent { Box { NoticeHost(notices) } }
        notices.show("Deleted Tabata", NoticeAction("Undo") { undone++ })
        onNodeWithText("Undo").performClick()
        assertEquals(1, undone)
        onNodeWithText("Deleted Tabata").assertDoesNotExist()
    }

    @Test
    fun `a notice with an action stays longer than one without`() = runUiTest {
        val notices = Notices()
        setThemedContent { Box { NoticeHost(notices) } }
        mainClock.autoAdvance = false
        notices.show("Deleted Tabata", NoticeAction("Undo") {})
        mainClock.advanceTimeBy(Motion.NOTICE_MILLIS + Motion.EXIT_MILLIS)
        onNodeWithText("Undo").assertIsDisplayed()
        mainClock.advanceTimeBy(Motion.UNDO_MILLIS)
        onNodeWithText("Undo").assertDoesNotExist()
    }

    @Test
    fun `a new notice replaces the one on show`() = runUiTest {
        val notices = Notices()
        setThemedContent { Box { NoticeHost(notices) } }
        notices.show("Saved as Tabata")
        onNodeWithText("Saved as Tabata").assertIsDisplayed()
        notices.show("Added to the plan")
        onNodeWithText("Added to the plan").assertIsDisplayed()
        onNodeWithText("Saved as Tabata").assertDoesNotExist()
    }
}
