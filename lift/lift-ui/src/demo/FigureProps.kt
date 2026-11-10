package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope

/** The bands tied to a post [behind] the figure, or the rest, held in front. */
internal fun DrawScope.drawBands(bands: List<Band>, figure: Figure, color: Color, behind: Boolean) {
    for (i in bands.indices) if (bands[i].behind(figure) == behind) drawBand(bands[i], figure, color)
}

/** A band tied to a post behind the figure, which runs behind it. */
private fun Band.behind(figure: Figure): Boolean = from.postBehind(figure) || to.postBehind(figure)

private fun BandEnd.postBehind(figure: Figure): Boolean = this is BandEnd.Post && at.x < figure.hip.x

/** A prop behind the figure: a bench's top and legs, a wall, a bar seen end on. */
internal fun DrawScope.drawProp(prop: Prop, color: Color) = when (prop) {
    is Bench -> drawBench(prop, color)

    is Wall -> drawLine(color, Offset(prop.x, Rig.FLOOR), Offset(prop.x, Rig.view.top), Rig.WALL)

    is Rail -> drawCircle(color, Rig.RAIL, prop.at)

    is Band -> {
        drawPost(prop.from, color)
        drawPost(prop.to, color)
    }
}

/** The post a band's [end] is tied to, if it is. */
private fun DrawScope.drawPost(end: BandEnd, color: Color) {
    if (end !is BandEnd.Post) return
    drawLine(color, Offset(end.at.x, Rig.FLOOR), end.at - Offset(0f, Rig.POST * 2), Rig.POST, StrokeCap.Round)
}

/** A band stretched between its two ends, where [figure] has them now. */
private fun DrawScope.drawBand(band: Band, figure: Figure, color: Color) =
    drawLine(color, figure.place(band.from), figure.place(band.to), Rig.BAND, StrokeCap.Round)

private fun DrawScope.drawBench(bench: Bench, color: Color) {
    val top = Rig.FLOOR - bench.height
    drawLine(color, Offset(bench.from, top), Offset(bench.to, top), Rig.BENCH_TOP, StrokeCap.Round)
    drawLine(color, Offset(bench.from, top), Offset(bench.from, Rig.FLOOR), Rig.BENCH_LEG)
    drawLine(color, Offset(bench.to, top), Offset(bench.to, Rig.FLOOR), Rig.BENCH_LEG)
}
