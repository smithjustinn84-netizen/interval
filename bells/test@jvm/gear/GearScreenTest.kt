@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.gear

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.justinsmith.bells.ui.bellsAppTest
import dev.justinsmith.bells.ui.waitForText
import kotlin.test.Test
import kotlin.test.assertEquals

/** Your equipment: the bells owned, the bar, and the bands, renamed, moved, archived and added. */
class GearScreenTest {
    private fun DesktopComposeUiTest.openEquipment() {
        onNodeWithContentDescription("Your equipment and records").performClick()
        waitForText("Your bands")
    }

    @Test
    fun `the bar chosen is stored and none clears it`() = bellsAppTest { app ->
        openEquipment()
        onNodeWithText("Your bar").performScrollTo()
        onNodeWithText("15 kg").performScrollTo().performClick()
        waitUntil { app.storage.bells.bar.value?.kg == 15.0 }
        onNodeWithText("None").performScrollTo().performClick()
        waitUntil { app.storage.bells.bar.value == null }
    }

    @Test
    fun `a band renamed keeps its place and is stored by its starting id`() = bellsAppTest { app ->
        openEquipment()
        onNodeWithText("Heavy").performScrollTo().performClick()
        waitForText("Colour")
        onNode(hasSetTextAction()).performTextInput("Blue loop")
        onNodeWithText("Save band").performScrollTo().performClick()
        waitForText("Blue loop")
        val row = app.storage.bands.rows.value.single()
        assertEquals(listOf("Heavy", "Blue loop", "3"), listOf(row.id, row.name, row.position.toString()))
    }

    @Test
    fun `a band moved heavier swaps with the next and one started with is archived not deleted`() =
        bellsAppTest { app ->
            openEquipment()
            onNodeWithText("Medium").performScrollTo().performClick()
            waitForText("Heavier")
            onNodeWithText("Heavier").performClick()
            onNodeWithText("Save band").performScrollTo().performClick()
            waitForText("Your bands")
            val order = app.storage.bands.rows.value.sortedBy { it.position }.map { it.id }
            assertEquals(listOf("ExtraLight", "Light", "Heavy", "Medium", "ExtraHeavy"), order)
            onNodeWithText("Extra light").performScrollTo().performClick()
            waitForText("Archive band")
            onNodeWithText("Archive band").performScrollTo().performClick()
            waitForText("Archived bands")
            assertEquals(true, app.storage.bands.rows.value.single { it.id == "ExtraLight" }.archived)
        }

    @Test
    fun `a band added needs a name and is heaviest`() = bellsAppTest { app ->
        openEquipment()
        onNodeWithText("Add band").performScrollTo().performClick()
        waitForText("Colour")
        onNodeWithText("Save band").assertDoesNotExist()
        onNode(hasSetTextAction()).performTextInput("Travel band")
        onNodeWithText("Orange").performClick()
        onNodeWithText("Save band").performScrollTo().performClick()
        waitForText("Travel band")
        val row = app.storage.bands.rows.value.single()
        assertEquals(listOf("Travel band", "Orange", "5"), listOf(row.name, row.colour, row.position.toString()))
    }
}
