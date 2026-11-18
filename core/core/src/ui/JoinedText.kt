package dev.justinsmith.interval.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle

/**
 * A line of parts joined by the app's dot ("about 35 min · Day 4 · Press · Row") that may run to a second line:
 * it wraps after a dot, never before one, so no line starts with the separator. It is drawn with the space before
 * each dot one that can't break, and read aloud, and found by a test, as [text] itself.
 */
@Composable
fun JoinedText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val drawn = remember(text) { text.replace(JOIN, JOIN_KEPT) }
    Text(drawn, modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }, style = style, color = color)
}

/** How two parts are joined (`common_joined` in each module's strings), and the same with the dot kept behind. */
private const val JOIN = " · "
private const val JOIN_KEPT = " · "
