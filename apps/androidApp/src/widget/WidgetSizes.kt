package dev.justinsmith.interval.widget

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The widget's own sizes; its spacing is core's `Spacing`. The ring is History's (`IntervalSizes.goalRing`). */
internal object WidgetSizes {
    val ring = 76.dp
    val ringStroke = 8.dp
    val ringCompact = 48.dp
    val ringStrokeCompact = 6.dp
    val button = 40.dp
    val buttonWide = 96.dp

    /** The shortcut's glyph on a round button, where a short widget has no room for its words. */
    val glyph = 24.dp

    /** A widget this tall stacks the ring over the buttons; below it, one row. */
    val tallFrom = 110.dp

    /** A widget this wide puts the words beside the ring and the buttons on the right. */
    val wideFrom = 220.dp

    val figure = 22.sp
    val figureCompact = 14.sp
    val caption = 14.sp
    val label = 14.sp
}
