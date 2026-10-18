package dev.justinsmith.interval.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import dev.justinsmith.interval.timer.IntervalConfig

/**
 * Round-by-round marks (the workout's dots, a timeline's segments) stop being readable past a
 * dozen rounds; beyond that the text carries the count and a timeline shows only the shares.
 */
const val MAX_ROUND_MARKS = 12

/**
 * The workout's shape as a bar in one hue: get ready in the surface variant, then each round's work
 * in the accent and its rest in a pale wash of it, each as wide as it is long. Work and rest differ
 * in lightness, not hue; phase colours stay on the workout screen and the field stripes. Past
 * [MAX_ROUND_MARKS] rounds the bar shows only the work and rest shares. Decorative: the text
 * beside it says the same thing.
 */
@Composable
fun Timeline(
    config: IntervalConfig,
    modifier: Modifier = Modifier,
    height: Dp = Sizes.timeline,
    gap: Dp = Sizes.timelineGap,
    showPrep: Boolean = true,
) {
    val palette = IntervalTheme.palette
    val work = palette.accent
    val rest = palette.timelineRest
    val segments = buildList {
        if (showPrep && config.prepSeconds > 0) add(palette.surfaceVariant to config.prepSeconds)
        if (config.rounds <= MAX_ROUND_MARKS) {
            repeat(config.rounds) { round ->
                add(work to config.workSeconds)
                if (round < config.rounds - 1 && config.restSeconds > 0) add(rest to config.restSeconds)
            }
        } else {
            add(work to config.rounds * config.workSeconds)
            val restTotal = (config.rounds - 1) * config.restSeconds
            if (restTotal > 0) add(rest to restTotal)
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(Corners.full)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        segments.forEach { (color, seconds) ->
            Box(Modifier.weight(seconds.toFloat()).fillMaxHeight().background(color))
        }
    }
}

/**
 * The ground of a card that can be the current choice, such as a preset: the surface with a line
 * border, or the accent's tint and the accent itself once [selected], crossfading between the two.
 * Also tells screen readers.
 */
@Composable
fun Modifier.selectableCard(selected: Boolean, shape: Shape = Corners.radius18): Modifier {
    val palette = IntervalTheme.palette
    val spec = tween<Color>(Motion.STATE_MILLIS, easing = StandardEasing)
    val fill by animateColorAsState(if (selected) palette.accentTint else palette.surface, spec, label = "fill")
    val line by animateColorAsState(if (selected) palette.accent else palette.line, spec, label = "line")
    return clip(shape)
        .background(fill)
        .border(Sizes.border, line, shape)
        .semantics { this.selected = selected }
}

/** A value in a pill of its own colour, on a pale wash of it: "0:20" in work red, "×8" in accent. */
@Composable
fun ValuePill(
    text: String,
    color: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    height: Dp = Sizes.valuePill,
    style: TextStyle = IntervalTheme.type.stat,
) {
    Box(
        modifier
            .height(height)
            .clip(Corners.radius12)
            .background(tint)
            .padding(horizontal = Spacing.space16),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = color)
    }
}

/** A [Timeline] at a list card's size: thin, and without the get-ready segment, which is the user's own. */
@Composable
fun MiniTimeline(config: IntervalConfig, modifier: Modifier = Modifier) {
    Timeline(config, modifier = modifier, height = Sizes.timelineMini, gap = Sizes.timelineMiniGap, showPrep = false)
}
