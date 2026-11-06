@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.ui.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test

class ExerciseFigureTest {
    @Test
    fun `a moving figure is described for a screen reader`() = runUiTest {
        setThemedContent {
            ExerciseFigure(TwoHandSwingDemo, contentDescription = "Two-hand swing", modifier = Modifier.size(200.dp))
        }
        onNodeWithContentDescription("Two-hand swing").assertIsDisplayed()
    }

    @Test
    fun `a figure draws one bell or two or leaning on a bench at any moment`() = runUiTest {
        val demos = listOf(GetUpDemo, ArmorBuildingComplexDemo, RowDemo, HaloDemo, SnatchDemo, FarmersWalkDemo)
        setThemedContent {
            Column {
                demos.forEach { demo ->
                    listOf(0f, 0.3f, 0.6f).forEach { at ->
                        ExerciseFigure(demo, { at }, contentDescription = null, modifier = Modifier.size(60.dp))
                    }
                }
            }
        }
        onRoot().captureToImage()
    }
}
