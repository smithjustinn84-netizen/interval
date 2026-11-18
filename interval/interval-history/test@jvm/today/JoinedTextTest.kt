@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.today

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.JoinedText
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals

class JoinedTextTest {
    @Test
    fun `a joined line is read as its own words however it is drawn to wrap`() = runUiTest {
        val line = "about 35 min · Day 4 of 40 · Press · Row · Goblet squat"
        setThemedContent {
            JoinedText(line, IntervalTheme.type.meta, IntervalTheme.palette.textDim, Modifier.width(120.dp))
        }

        val said = onNodeWithText(line).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
        assertEquals(listOf(line), said?.map { it.text })
    }
}
