package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.hypot

/** The bands tied to a post [behind] the figure, or the rest, held in front. */
internal fun DrawScope.drawBands(bands: List<Band>, figure: Figure, color: Color, behind: Boolean) {
    for (i in bands.indices) if (bands[i].behind(figure) == behind) drawBand(bands[i], figure, color)
}

/** A band tied to a post behind the figure, which runs behind it. */
private fun Band.behind(figure: Figure): Boolean = from.postBehind(figure) || to.postBehind(figure)

private fun BandEnd.postBehind(figure: Figure): Boolean = this is BandEnd.Post && at.x < figure.hip.x

/** A prop behind the figure: a bench's padded top and legs, a wall, a bar seen end on, or a band's post. */
internal fun DrawScope.drawProp(prop: Prop, color: Color) = when (prop) {
    is Bench -> drawBench(prop, color)

    is Wall -> drawWall(prop, color)

    is Rail -> drawRail(prop, color)

    is Band -> {
        drawPost(prop.from, color)
        drawPost(prop.to, color)
    }
}

/** The post a band's [end] is tied to, if it is: upright with a floor base and an anchor collar. */
private fun DrawScope.drawPost(end: BandEnd, color: Color) {
    if (end !is BandEnd.Post) return
    val x = end.at.x
    drawLine(color, Offset(x, Rig.FLOOR), end.at - Offset(0f, Rig.POST * 2), Rig.POST, StrokeCap.Round)
    drawLine(
        color,
        Offset(x - POST_BASE_HALF, Rig.FLOOR),
        Offset(x + POST_BASE_HALF, Rig.FLOOR),
        POST_BASE_STROKE,
        StrokeCap.Round,
    )
    drawCircle(color, POST_COLLAR_RADIUS, end.at)
}

/**
 * A band stretched between its two ends: a tensioned double-strand look (main band plus a subtle offset twin
 * strand) and anchor knots where it meets a post.
 */
private fun DrawScope.drawBand(band: Band, figure: Figure, color: Color) {
    val a = figure.place(band.from)
    val b = figure.place(band.to)
    drawLine(color, a, b, Rig.BAND, StrokeCap.Round)
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = hypot(dx, dy)
    if (len > MIN_BAND_LENGTH) {
        val offset = Offset(-dy / len * BAND_STRAND_OFFSET, dx / len * BAND_STRAND_OFFSET)
        drawLine(
            color.copy(alpha = BAND_TWIN_ALPHA),
            a + offset,
            b + offset,
            BAND_TWIN_STROKE,
            StrokeCap.Round,
        )
    }
    if (band.from is BandEnd.Post) drawCircle(color, BAND_KNOT_RADIUS, a)
    if (band.to is BandEnd.Post) drawCircle(color, BAND_KNOT_RADIUS, b)
}

/** A bench or plyo box: padded top cushion, subtle frame rail, and inset legs resting on the floor. */
private fun DrawScope.drawBench(bench: Bench, color: Color) {
    val top = Rig.FLOOR - bench.height
    val legInset = ((bench.to - bench.from) * BENCH_LEG_INSET_RATIO).coerceAtMost(BENCH_LEG_MAX_INSET)
    val leftLeg = bench.from + legInset
    val rightLeg = bench.to - legInset
    val frameY = top + Rig.BENCH_TOP * BENCH_FRAME_DROP
    // Inset legs and underside frame rail.
    drawLine(
        color.copy(alpha = BENCH_FRAME_ALPHA),
        Offset(leftLeg, frameY),
        Offset(rightLeg, frameY),
        BENCH_FRAME_STROKE,
    )
    drawLine(
        color.copy(alpha = BENCH_LEG_ALPHA),
        Offset(leftLeg, top),
        Offset(leftLeg, Rig.FLOOR),
        Rig.BENCH_LEG,
        StrokeCap.Round,
    )
    drawLine(
        color.copy(alpha = BENCH_LEG_ALPHA),
        Offset(rightLeg, top),
        Offset(rightLeg, Rig.FLOOR),
        Rig.BENCH_LEG,
        StrokeCap.Round,
    )
    // Padded top cushion.
    drawLine(color, Offset(bench.from, top), Offset(bench.to, top), Rig.BENCH_TOP, StrokeCap.Round)
    drawLine(
        Color.White.copy(alpha = BENCH_HIGHLIGHT_ALPHA),
        Offset(bench.from + BENCH_HIGHLIGHT_INSET, top - BENCH_HIGHLIGHT_LIFT),
        Offset(bench.to - BENCH_HIGHLIGHT_INSET, top - BENCH_HIGHLIGHT_LIFT),
        BENCH_HIGHLIGHT_STROKE,
        StrokeCap.Round,
    )
}

/** A wall surface with a soft solid backing strip behind it so the solid side reads immediately. */
private fun DrawScope.drawWall(wall: Wall, color: Color) {
    val top = Rig.view.top
    val height = Rig.FLOOR - top
    val backingLeft = if (wall.x < Rig.ANKLE_X) wall.x - WALL_BACKING_WIDTH else wall.x
    drawRect(
        color.copy(alpha = WALL_BACKING_ALPHA),
        topLeft = Offset(backingLeft, top),
        size = Size(WALL_BACKING_WIDTH, height),
    )
    drawLine(color, Offset(wall.x, Rig.FLOOR), Offset(wall.x, top), Rig.WALL, StrokeCap.Round)
}

/**
 * What holds a bar up, behind the figure: from a bar overhead a bracket up to the ceiling ([top]), from a low one
 * an upright down to the floor with a base, as a rack's is. Without it a bar seen end on is a dot in the fists.
 */
internal fun DrawScope.drawRailStand(rail: Rail, color: Color, top: Float) {
    val overhead = rail.at.y < Rig.FLOOR / 2
    val end = if (overhead) Offset(rail.at.x, top) else Offset(rail.at.x, Rig.FLOOR)
    drawLine(color.copy(alpha = RAIL_STAND_ALPHA), rail.at, end, Rig.POST, StrokeCap.Round)
    val across = Offset(POST_BASE_HALF, 0f)
    drawLine(color.copy(alpha = RAIL_STAND_ALPHA), end - across, end + across, POST_BASE_STROKE, StrokeCap.Round)
}

/** A pull-up or dip bar seen end on: outer steel ring and bright center core. */
private fun DrawScope.drawRail(rail: Rail, color: Color) {
    drawCircle(color.copy(alpha = RAIL_FILL_ALPHA), Rig.RAIL, rail.at)
    drawCircle(color, Rig.RAIL - RAIL_RIM_STROKE * HALF, rail.at, style = RailRimStroke)
    drawCircle(Color.White.copy(alpha = RAIL_CORE_ALPHA), RAIL_CORE_RADIUS, rail.at)
}

private val RailRimStroke = Stroke(RAIL_RIM_STROKE)

private const val HALF = 0.5f
private const val MIN_BAND_LENGTH = 1f
private const val BAND_STRAND_OFFSET = 3.2f
private const val BAND_TWIN_ALPHA = 0.5f
private const val BAND_TWIN_STROKE = 2.2f
private const val BAND_KNOT_RADIUS = 5.2f

private const val POST_BASE_HALF = 11f
private const val POST_BASE_STROKE = 4f
private const val POST_COLLAR_RADIUS = 4.8f

private const val BENCH_LEG_INSET_RATIO = 0.12f
private const val BENCH_LEG_MAX_INSET = 14f
private const val BENCH_FRAME_DROP = 0.7f
private const val BENCH_FRAME_ALPHA = 0.55f
private const val BENCH_FRAME_STROKE = 3.5f
private const val BENCH_LEG_ALPHA = 0.8f
private const val BENCH_HIGHLIGHT_ALPHA = 0.16f
private const val BENCH_HIGHLIGHT_INSET = 4f
private const val BENCH_HIGHLIGHT_LIFT = 1.8f
private const val BENCH_HIGHLIGHT_STROKE = 2f

private const val WALL_BACKING_WIDTH = 14f
private const val WALL_BACKING_ALPHA = 0.18f

private const val RAIL_FILL_ALPHA = 0.55f
private const val RAIL_STAND_ALPHA = 0.7f
private const val RAIL_RIM_STROKE = 2.6f
private const val RAIL_CORE_RADIUS = 2.4f
private const val RAIL_CORE_ALPHA = 0.55f
