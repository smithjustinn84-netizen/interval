package dev.justinsmith.interval.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The few line icons the app needs: navigation tabs and top-bar actions, where both platforms expect
 * a pictogram. Everything else stays a word, as the design system asks. Drawn on a 24-unit grid with
 * a 2-unit round stroke; `Icon(tint = …)` colours them.
 */
object IntervalIcons {
    val Timer: ImageVector = lineIcon(
        "Timer",
        "M20 13a8 8 0 1 1 -16 0a8 8 0 1 1 16 0",
        "M12 9v4l2.5 2.5M10 2h4",
    )
    val Presets: ImageVector = lineIcon("Presets", "M4 6h16M4 12h16M4 18h10")
    val History: ImageVector = lineIcon("History", "M3 12a9 9 0 1 0 3-6.7L3 8", "M3 3v5h5M12 7v5l3 2")
    val Settings: ImageVector = lineIcon(
        "Settings",
        "M15 12a3 3 0 1 1 -6 0a3 3 0 1 1 6 0",
        "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 " +
            "1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06" +
            "a2 2 0 0 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09" +
            "A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 " +
            "1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 " +
            "1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21" +
            "a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z",
    )
    val Add: ImageVector = lineIcon("Add", "M12 5v14M5 12h14")
    val Back: ImageVector = lineIcon("Back", "M19 12H5M12 19l-7-7 7-7")
    val Close: ImageVector = lineIcon("Close", "M6 6l12 12M18 6L6 18")
    val Chevron: ImageVector = lineIcon("Chevron", "M9 6l6 6-6 6")
    val Check: ImageVector = lineIcon("Check", "M5 12l5 5L20 7")

    /** The filled triangle inside Start workout. */
    val Play: ImageVector = ImageVector.Builder("Play", 24.dp, 24.dp, VIEWPORT, VIEWPORT).apply {
        addPath(
            addPathNodes("M7 4.5v15a1 1 0 0 0 1.52.85l12-7.5a1 1 0 0 0 0-1.7l-12-7.5A1 1 0 0 0 7 4.5z"),
            fill = SolidColor(Color.Black),
        )
    }.build()
    val More: ImageVector = ImageVector.Builder("More", 24.dp, 24.dp, VIEWPORT, VIEWPORT).apply {
        listOf(5f, 12f, 19f).forEach { y ->
            addPath(addPathNodes("M14 ${y}a2 2 0 1 1 -4 0a2 2 0 1 1 4 0"), fill = SolidColor(Color.Black))
        }
    }.build()
}

private const val VIEWPORT = 24f

private fun lineIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, VIEWPORT, VIEWPORT).apply {
        paths.forEach { path ->
            addPath(
                pathData = addPathNodes(path),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
