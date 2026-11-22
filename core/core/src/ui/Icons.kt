@file:Suppress("detekt.MagicNumber")

package dev.justinsmith.interval.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path

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

    /** A calendar page: today's training. */
    val Today: ImageVector =
        lineIcon(
            "Today",
            "M4 6a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z",
            "M4 10h16M8 2v4M16 2v4",
        )

    /** A kettlebell: its handle and its bell. */
    val Bell: ImageVector = lineIcon("Bell", "M9 9V7a3 3 0 0 1 6 0v2", "M18 15a6 6 0 1 1 -12 0a6 6 0 1 1 12 0")

    /** A pennant on its pole: a day of a sport trained away from the app. */
    val Sport: ImageVector = lineIcon("Sport", "M6 21V4", "M6 4.5l12 4.25L6 13")
    val Back: ImageVector = lineIcon("Back", "M19 12H5M12 19l-7-7 7-7", autoMirror = true)
    val Close: ImageVector = lineIcon("Close", "M6 6l12 12M18 6L6 18")
    val Chevron: ImageVector = lineIcon("Chevron", "M9 6l6 6-6 6", autoMirror = true)

    /** A vertical disclosure chevron (Filters, Licenses), unmirrored so up and down stay up and down in RTL. */
    val ChevronDown: ImageVector = lineIcon("ChevronDown", "M6 9l6 6 6-6")

    val Check: ImageVector = lineIcon("Check", "M5 12l5 5L20 7")

    /** Two bars: a row's handle, dragged to put it in another place. */
    val DragHandle: ImageVector = lineIcon("DragHandle", "M5 9h14M5 15h14")

    /** The Timer's way to describe a workout in words: lines of text and a pencil. */
    val Describe: ImageVector = lineIcon("Describe", "M4 6h16M4 11h9M4 16h5", "M14 20v-2.5l5.5-5.5 2.5 2.5-5.5 5.5z")

    /** The way back a month on History's calendar: [Chevron] turned round. */
    val ChevronBack: ImageVector = lineIcon("ChevronBack", "M15 6l-6 6 6 6", autoMirror = true)

    /** History's way to the plan: a calendar page with a plus. */
    val Plan: ImageVector = lineIcon(
        "Plan",
        "M6 5h12a3 3 0 0 1 3 3v10a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-10a3 3 0 0 1 3 -3z",
        "M3 10h18M8 3v4M16 3v4M12 13v5M9.5 15.5h5",
    )

    /** The weekly streak's flame, beside its count. */
    val Streak: ImageVector = lineIcon(
        "Streak",
        "M12 3c1 4 5 5 5 10a5 5 0 0 1 -10 0c0 -2 1 -3 2 -4c0 2 1 3 2 3c0 -3 -1 -5 1 -9z",
    )

    /** A calm crescent: a rest day on Today's coach banner. */
    val Rest: ImageVector = lineIcon(
        "Rest",
        "M20.5 14.5A8.5 8.5 0 1 1 9.5 3.5a6.8 6.8 0 0 0 11 11z",
    )

    /** A gentle easing wave: a taxed day to take a notch easier on Today's coach banner. */
    val Ease: ImageVector = lineIcon(
        "Ease",
        "M3 10c2.5 -3 5.5 -3 8 0s5.5 3 10 0",
        "M3 16c2.5 -3 5.5 -3 8 0s5.5 3 10 0",
    )

    /** A filled heart: live heart rate beside the BPM readout. */
    val Heart: ImageVector = ImageVector.Builder("Heart", Sizes.icon, Sizes.icon, VIEWPORT, VIEWPORT).apply {
        addPath(
            addPathNodes(
                "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 " +
                    "2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09 " +
                    "C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5 " +
                    "c0 3.78-3.4 6.86-8.55 11.54L12 21.35z",
            ),
            fill = SolidColor(Color.Black),
        )
    }.build()

    /** The filled triangle inside Start workout. */
    val Play: ImageVector = ImageVector.Builder("Play", Sizes.icon, Sizes.icon, VIEWPORT, VIEWPORT).apply {
        addPath(
            addPathNodes("M7 4.5v15a1 1 0 0 0 1.52.85l12-7.5a1 1 0 0 0 0-1.7l-12-7.5A1 1 0 0 0 7 4.5z"),
            fill = SolidColor(Color.Black),
        )
    }.build()

    /** Two filled bars: what holds something that runs by itself, where [Play] sets it going. */
    val Pause: ImageVector = ImageVector.Builder("Pause", Sizes.icon, Sizes.icon, VIEWPORT, VIEWPORT).apply {
        listOf(6f, 14f).forEach { x ->
            addPath(
                addPathNodes("M${x + 1} 4.5h2a1 1 0 0 1 1 1v13a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1v-13a1 1 0 0 1 1 -1z"),
                fill = SolidColor(Color.Black),
            )
        }
    }.build()
    val More: ImageVector = ImageVector.Builder("More", Sizes.icon, Sizes.icon, VIEWPORT, VIEWPORT).apply {
        listOf(5f, 12f, 19f).forEach { y ->
            addPath(addPathNodes("M14 ${y}a2 2 0 1 1 -4 0a2 2 0 1 1 4 0"), fill = SolidColor(Color.Black))
        }
    }.build()

    /** Kettlebell swing: a forward pendulum arc driving the bell up to chest-height float. */
    val KbSwing: ImageVector = strokeIcon("KbSwing") {
        kettlebell(cx = 17.2f, cy = 10.2f, radius = 3.6f)
        strokePath {
            moveTo(3.5f, 13.5f)
            curveTo(4.5f, 20f, 10.2f, 20.5f, 13.2f, 16.2f)
            moveTo(9.8f, 16f)
            lineTo(13.5f, 15.8f)
            lineTo(13f, 19.5f)
        }
    }

    /** Kettlebell clean: a tight upward pull curling inward into the mid-height rack. */
    val KbClean: ImageVector = strokeIcon("KbClean") {
        kettlebell(cx = 17.2f, cy = 14.8f)
        strokePath {
            moveTo(4.5f, 20.5f)
            verticalLineTo(10.8f)
            curveTo(4.5f, 6.5f, 8.8f, 6f, 11.4f, 8.8f)
            moveTo(9.2f, 5.8f)
            lineTo(11.8f, 9f)
            lineTo(8.5f, 10.5f)
        }
    }

    /** Kettlebell snatch: an explosive floor-to-overhead S-curve punching straight into lockout. */
    val KbSnatch: ImageVector = strokeIcon("KbSnatch") {
        kettlebell(cx = 17.4f, cy = 9.6f, radius = 3.5f)
        strokePath {
            moveTo(4f, 20.5f)
            curveTo(8.5f, 20f, 9.6f, 16.5f, 8.6f, 12.8f)
            reflectiveCurveTo(8.2f, 6f, 10.4f, 4.4f)
            moveTo(7.4f, 6.4f)
            lineTo(10.5f, 4.1f)
            lineTo(12f, 7.4f)
        }
    }

    /** Kettlebell press: a strict vertical drive pushing the bell straight overhead. */
    val KbPress: ImageVector = strokeIcon("KbPress") {
        kettlebell(cx = 12f, cy = 9.6f, radius = 3.8f)
        strokePath {
            moveTo(12f, 21f)
            verticalLineTo(15.5f)
            moveTo(8f, 18.5f)
            lineTo(12f, 15.2f)
            lineTo(16f, 18.5f)
        }
    }

    /** Turkish get-up: rising from a supine floor start to standing under an overhead bell. */
    val TurkishGetUp: ImageVector = strokeIcon("TurkishGetUp") {
        kettlebell(cx = 17.6f, cy = 9.6f, radius = 3.2f)
        strokePath {
            moveTo(3f, 20.5f)
            horizontalLineTo(21f)
            moveTo(3.5f, 17f)
            horizontalLineTo(8.5f)
            moveTo(17.6f, 20.5f)
            verticalLineTo(12.8f)
            moveTo(6.2f, 13.5f)
            curveTo(8.2f, 10.5f, 10f, 8.6f, 12f, 7.2f)
            moveTo(8.8f, 6.8f)
            lineTo(12.3f, 7f)
            lineTo(11.6f, 10.4f)
        }
    }

    /** EMOM timer: a stopwatch dial looping clockwise back to the top of every minute. */
    val TimerEmom: ImageVector = strokeIcon("TimerEmom") {
        strokePath {
            moveTo(10f, 2.5f)
            horizontalLineTo(14f)
            moveTo(16.8f, 7.8f)
            arcTo(7.5f, 7.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 19.3f, 15.2f)
            moveTo(14f, 7.6f)
            lineTo(17.1f, 8f)
            lineTo(17.3f, 4.9f)
            moveTo(12f, 13.5f)
            verticalLineTo(9.2f)
        }
    }

    /** Tabata timer: a 2:1 segmented work/rest stopwatch dial with a high-intensity bolt. */
    val TimerTabata: ImageVector = strokeIcon("TimerTabata") {
        strokePath {
            moveTo(10f, 2.5f)
            horizontalLineTo(14f)
            moveTo(12f, 6f)
            arcTo(7.5f, 7.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 5.5f, 17.2f)
            moveTo(4.7f, 11.7f)
            arcTo(7.5f, 7.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.3f, 7f)
            moveTo(13f, 9.6f)
            lineTo(10.2f, 13.5f)
            horizontalLineTo(13.8f)
            lineTo(11f, 17.4f)
        }
    }

    /** AMRAP timer: a continuous countdown stopwatch dial with stacking upward round chevrons. */
    val TimerAmrap: ImageVector = strokeIcon("TimerAmrap") {
        strokePath {
            moveTo(10f, 2.5f)
            horizontalLineTo(14f)
            moveTo(19.5f, 13.5f)
            arcTo(7.5f, 7.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4.5f, 13.5f)
            arcTo(7.5f, 7.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 19.5f, 13.5f)
            moveTo(9.2f, 12.6f)
            lineTo(12f, 9.8f)
            lineTo(14.8f, 12.6f)
            moveTo(9.2f, 16.4f)
            lineTo(12f, 13.6f)
            lineTo(14.8f, 16.4f)
        }
    }
}

private const val VIEWPORT = 24f
private const val STROKE_WIDTH = 2f
private const val BELL_RADIUS = 3.6f

private fun lineIcon(name: String, vararg paths: String, autoMirror: Boolean = false): ImageVector =
    ImageVector.Builder(name, Sizes.icon, Sizes.icon, VIEWPORT, VIEWPORT, autoMirror = autoMirror).apply {
        paths.forEach { path ->
            addPath(
                pathData = addPathNodes(path),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

private fun strokeIcon(
    name: String,
    autoMirror: Boolean = false,
    build: ImageVector.Builder.() -> Unit,
): ImageVector = ImageVector.Builder(name, Sizes.icon, Sizes.icon, VIEWPORT, VIEWPORT, autoMirror = autoMirror)
    .apply(build)
    .build()

private fun ImageVector.Builder.strokePath(block: PathBuilder.() -> Unit) {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )
}

private fun ImageVector.Builder.fillPath(block: PathBuilder.() -> Unit) {
    path(
        fill = SolidColor(Color.Black),
        pathBuilder = block,
    )
}

private fun ImageVector.Builder.kettlebell(cx: Float, cy: Float, radius: Float = BELL_RADIUS) {
    val belly = radius * 1.06f
    val handleHalfWidth = radius * 0.62f
    val stemBase = cy - radius * 0.68f
    val shoulder = cy - radius * 1.52f
    val archControl = cy - radius * 2.12f
    fillPath {
        moveTo(cx - belly, cy)
        arcTo(belly, radius, 0f, isMoreThanHalf = true, isPositiveArc = false, cx + belly, cy)
        arcTo(belly, radius, 0f, isMoreThanHalf = true, isPositiveArc = false, cx - belly, cy)
        close()
    }
    strokePath {
        moveTo(cx - handleHalfWidth, stemBase)
        verticalLineTo(shoulder)
        curveTo(cx - handleHalfWidth, archControl, cx + handleHalfWidth, archControl, cx + handleHalfWidth, shoulder)
        verticalLineTo(stemBase)
    }
}
