package dev.justinsmith.interval.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp

/**
 * Past this font scale (the system's text size, 1 at its default) a screen trades what it shows beside for what
 * it can say whole: a week's days by their initials, the Timer's rows scrolling rather than sharing the height.
 */
const val LARGE_FONT_SCALE = 1.35f

/** Whether the system's text is large enough, past [LARGE_FONT_SCALE], that a crowded row should give way. */
@Composable
fun isLargeText(): Boolean = LocalDensity.current.fontScale > LARGE_FONT_SCALE

/**
 * [content] with the system's text growing no further than [max]: for words held in a shape of a fixed size, such as
 * a ring, that would run over it past that. Text the reader needs whole goes elsewhere at full size; this is for
 * what a shape frames, which says it again beside it or reads it aloud whole all the same.
 */
@Composable
fun TextScaleAtMost(max: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    if (density.fontScale <= max) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(density.density, max), content = content)
    }
}

/**
 * [main] with [trailing] at its end, or under it when beside it [main]'s longest word would not fit: text squeezed
 * narrower than a word breaks inside it ("Two-ha / nd swing"), which a reader can't follow. At an ordinary size
 * this is a row like any other, [main] wrapping between its words beside [trailing]; only with large text, or a
 * long word in a narrow window, does [trailing] drop under it, [gap] below and [underStart] in (where the words
 * start, in a row that leads with a mark). With [under] it goes under whatever fits: a row whose text wants
 * more than its longest word, such as a name a line rather than a word a line, asks for it with large text.
 * [main]'s own measure of its longest word is taken, or [mainMinWidth] (pixels) where that is more: Android's
 * counts a hyphen as a place to break, then breaks "Two-hand" inside "hand", so text that matters whole passes
 * [longestWordWidth].
 */
@Composable
fun BesideOrUnder(
    main: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    gap: Dp,
    modifier: Modifier = Modifier,
    mainMinWidth: Int = 0,
    underStart: Dp = 0.dp,
    under: Boolean = false,
) {
    Layout(contents = listOf(main, trailing), modifier = modifier) { (mains, trailings), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val end = trailings.map { it.measure(loose) }
        val gapPx = gap.roundToPx()
        val room = (constraints.maxWidth - end.widest() - gapPx).coerceAtLeast(0)
        val longestWord = maxOf(mainMinWidth, mains.maxOfOrNull { it.minIntrinsicWidth(Constraints.Infinity) } ?: 0)
        if (constraints.hasBoundedWidth && (under || longestWord > room)) {
            // Under: the main part the whole width, the trailing part below it.
            val top = mains.map { it.measure(loose) }
            val width = constraints.constrainWidth(maxOf(top.widest(), end.widest()))
            layout(width, constraints.constrainHeight(top.tallest() + gapPx + end.tallest())) {
                top.forEach { it.place(0, 0) }
                end.forEach { it.place(underStart.roundToPx(), top.tallest() + gapPx) }
            }
        } else {
            // Beside: the main part in what is left, the trailing part at the end, both centred on one line.
            val start = mains.map { it.measure(loose.copy(maxWidth = room)) }
            val height = maxOf(start.tallest(), end.tallest())
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else start.widest() + gapPx + end.widest()
            layout(width, constraints.constrainHeight(height)) {
                start.forEach { it.place(0, (height - it.height) / 2) }
                end.forEach { it.place(width - it.width, (height - it.height) / 2) }
            }
        }
    }
}

private fun List<Placeable>.widest(): Int = maxOfOrNull { it.width } ?: 0

private fun List<Placeable>.tallest(): Int = maxOfOrNull { it.height } ?: 0

/** The width in pixels of [text]'s widest run between spaces, in [style]: what it takes to keep each word whole. */
@Composable
fun longestWordWidth(text: String, style: TextStyle): Int {
    val measurer = rememberTextMeasurer()
    return remember(text, style, measurer) {
        text.split(' ').filter { it.isNotEmpty() }.maxOfOrNull { word ->
            measurer.measure(word, style, softWrap = false, maxLines = 1).size.width
        } ?: 0
    }
}
