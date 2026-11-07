@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.presets

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

/** The presets as Train's Conditioning pillar: the Timer card over the groups. */
class PresetsTabTest {
    private val saved = mutableListOf<Preset>()
    private var timerOpened = 0

    /** The tab's page, with the Timer card; its pillar picker stands in as a word. */
    private val tab = PresetsTab("Train", onOpenSettings = {}, onOpenTimer = { timerOpened++ }) { Text("Pillars") }

    @Composable
    private fun Screen(state: PresetsUiState, tab: PresetsTab? = this.tab) {
        PresetsScreen(
            state = state,
            onStart = {},
            onPutOnTimer = {},
            onSave = { saved += it },
            onSaveCurrent = { saved += it },
            onDelete = {},
            tab = tab,
        )
    }

    @Test
    fun `the Timer row says what is on it and offers to save a shape no preset has`() = runUiTest {
        var state by mutableStateOf(state())
        setThemedContent { Screen(state) }
        onNodeWithText("Pillars").assertExists()
        // The fixture's current shape, 40/20 ×6 after 10s to get ready.
        onNode(hasText("Timer") and hasText("40s on · 20s off · ×6") and hasText("5:50")).assertExists()
        onNodeWithText("Save as preset").performClick()
        inSheet("Save as preset").assertExists()
        saveInEditor()
        assertEquals(current, saved.single().config)

        state = state(canSaveCurrent = false, selected = morning)
        onNodeWithText("Save as preset").assertDoesNotExist()
        onNode(hasText("Timer") and hasText("Morning")).performClick()
        assertEquals(1, timerOpened)
    }

    @Test
    fun `pushed over the Timer the page has no Timer card`() = runUiTest {
        setThemedContent { Screen(state(), tab = null) }
        onNodeWithText("Timer").assertDoesNotExist()
        onNodeWithText("Save as preset").assertDoesNotExist()
    }
}
