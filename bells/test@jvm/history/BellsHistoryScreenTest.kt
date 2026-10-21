@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.history

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import dev.justinsmith.bells.ui.runUiTest
import dev.justinsmith.bells.ui.setBellsContent
import kotlin.test.Test
import kotlin.test.assertEquals

private val tabata = HistoryDay(DayHeading.Today, listOf(HistoryRow.Interval("Tabata", "7:42 AM", "4:00")))

class BellsHistoryScreenTest {
    @Test
    fun `an Interval workout opens Interval`() = runUiTest {
        var opened = 0
        setBellsContent { BellsHistoryScreen(listOf(tabata), onDelete = {}, onOpenInterval = { opened++ }) }
        onNode(hasText("Tabata") and hasClickAction()).performClick()
        assertEquals(1, opened)
        onNode(
            SemanticsMatcher("labelled Open in Interval") {
                it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Open in Interval"
            },
        ).assertExists()
    }

    @Test
    fun `an Interval workout is only a row where Interval can't be opened`() = runUiTest {
        setBellsContent { BellsHistoryScreen(listOf(tabata), onDelete = {}) }
        onNode(hasText("Tabata") and hasClickAction()).assertDoesNotExist()
    }
}
