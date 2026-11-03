package dev.justinsmith.interval.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.ui.Angles
import dev.justinsmith.interval.ui.PhaseColors
import kotlin.math.roundToInt

/**
 * The week's ring as History draws it, on a bitmap for the widget (a remote view has no canvas): the track a
 * full circle, the share of the goal an arc from twelve o'clock with a round cap, nothing of it with no goal
 * set. [diameter] and [stroke] in dp, drawn at the device's density; the figure is laid over it as text.
 */
internal fun weekRing(context: Context, standing: WeekStanding, diameter: Dp, stroke: Dp): Bitmap {
    val density = context.resources.displayMetrics.density
    val px = (diameter.value * density).roundToInt()
    val strokePx = stroke.value * density
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
    }
    val inset = strokePx / 2
    val bounds = RectF(inset, inset, px - inset, px - inset)
    paint.color = PhaseColors.upcoming.toArgb()
    canvas.drawArc(bounds, 0f, Angles.FULL_TURN, false, paint)
    val goal = standing.goal ?: return bitmap
    val share = (standing.count.toFloat() / goal).coerceAtMost(1f)
    if (share > 0f) {
        paint.color = PhaseColors.done.toArgb()
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(bounds, Angles.TWELVE_O_CLOCK, Angles.FULL_TURN * share, false, paint)
    }
    return bitmap
}
