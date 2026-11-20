@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.ui

import androidx.compose.ui.test.ExperimentalTestApi
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.DEFAULT_BAR_KG
import kotlin.test.Test

class EquipmentGraphicsTest {
    @Test
    fun `barbell diagram composes for loaded, empty, and unrepresentable weights`() = runUiTest {
        setThemedContent {
            BarbellDiagram(totalKg = 102.5, barKg = DEFAULT_BAR_KG)
            BarbellDiagram(totalKg = DEFAULT_BAR_KG, barKg = DEFAULT_BAR_KG)
            BarbellDiagram(totalKg = 10.0, barKg = DEFAULT_BAR_KG)
        }
        waitForIdle()
    }

    @Test
    fun `kettlebell rack composes for empty and owned sets`() = runUiTest {
        setThemedContent {
            KettlebellRack(emptySet())
            KettlebellRack(setOf(12.0, 16.0, 24.0, 32.0))
        }
        waitForIdle()
    }
}
