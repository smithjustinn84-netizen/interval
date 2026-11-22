@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.ui.LocalWeightUnit
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import dev.justinsmith.lift.domain.DEFAULT_BAR_KG
import dev.justinsmith.lift.domain.lbToKg
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

    @Test
    fun `barbell diagram and kettlebell rack render in pounds`() = runUiTest {
        setThemedContent {
            CompositionLocalProvider(LocalWeightUnit provides WeightUnit.Lb) {
                BarbellDiagram(totalKg = lbToKg(140.0), barKg = DEFAULT_BAR_KG)
                KettlebellRack(setOf(16.0, 24.0, 32.0))
            }
        }
        onNodeWithText("35", useUnmergedTree = true).assertExists()
        onNodeWithText("53", useUnmergedTree = true).assertExists()
        onNodeWithText("70", useUnmergedTree = true).assertExists()
    }
}
