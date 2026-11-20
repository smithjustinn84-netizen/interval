package dev.justinsmith.interval.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import dev.justinsmith.interval.timer.Block
import dev.justinsmith.interval.timer.Course
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Phase

/**
 * Round-by-round marks (the workout's dots) stop being readable past a dozen rounds; beyond that the text carries
 * the count.
 */
const val MAX_ROUND_MARKS = 12

/** Individual round peaks drawn on a waveform before falling back to block-level summary bars. */
private const val MAX_WAVEFORM_ROUNDS = 40

/**
 * The workout's intensity profile as a stepped waveform: work intervals rise to full height in the accent, warm-up
 * and cool-down sit at mid-height in a medium wash, rests sit low in [IntervalPalette.timelineRest], and get-ready
 * leads in subtly in [IntervalPalette.surfaceVariant]. Each bar's width is proportional to its duration. Decorative:
 * the text beside it says the same thing.
 */
@Composable
fun Timeline(
    config: IntervalConfig,
    modifier: Modifier = Modifier,
    height: Dp = IntervalSizes.timeline,
    gap: Dp = IntervalSizes.timelineGap,
    showPrep: Boolean = true,
) {
    val palette = IntervalTheme.palette
    val segments = remember(config, showPrep) { timelineSegments(config, showPrep) }
    val workColor = palette.accent
    val bookendColor = palette.accent.copy(alpha = BOOKEND_ALPHA)
    val restColor = palette.timelineRest
    val prepColor = palette.surfaceVariant
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics {},
    ) {
        drawWaveform(segments, gap.toPx(), workColor, bookendColor, restColor, prepColor)
    }
}

private fun DrawScope.drawWaveform(
    segments: List<Pair<Phase, Int>>,
    gapPx: Float,
    workColor: Color,
    bookendColor: Color,
    restColor: Color,
    prepColor: Color,
) {
    val totalSeconds = segments.sumOf { it.second }
    if (segments.isEmpty() || totalSeconds <= 0) return
    val count = segments.size
    // Shrink gap smoothly when many rounds are packed into a narrow card so bars never vanish.
    val maxGap = if (count > 1) (size.width * MAX_GAP_SHARE / (count - 1)).coerceAtMost(gapPx) else 0f
    val availableWidth = (size.width - maxGap * (count - 1)).coerceAtLeast(1f)

    var x = 0f
    for (i in segments.indices) {
        val (phase, seconds) = segments[i]
        val w = (availableWidth * (seconds.toFloat() / totalSeconds)).coerceAtLeast(MIN_BAR_PX)
        val h = size.height * phaseHeight(phase)
        val color = when (phase) {
            Phase.Prep -> prepColor
            Phase.Work -> workColor
            Phase.WarmUp, Phase.CoolDown -> bookendColor
            Phase.Rest, Phase.SetRest, Phase.Done -> restColor
        }
        val radius = CornerRadius(minOf(w / 2f, h / 2f, MAX_CORNER_PX))
        drawRoundRect(
            color = color,
            topLeft = Offset(x, size.height - h),
            size = Size(w, h),
            cornerRadius = radius,
        )
        x += w + maxGap
    }
}

private fun phaseHeight(phase: Phase): Float = when (phase) {
    Phase.Work -> WORK_HEIGHT
    Phase.WarmUp, Phase.CoolDown -> BOOKEND_HEIGHT
    Phase.Rest, Phase.SetRest, Phase.Done -> REST_HEIGHT
    Phase.Prep -> PREP_HEIGHT
}

internal fun timelineSegments(config: IntervalConfig, showPrep: Boolean): List<Pair<Phase, Int>> {
    val course = Course.of(config).filter { showPrep || it.phase != Phase.Prep }
    if (config.blocks.sumOf { it.rounds } <= MAX_WAVEFORM_ROUNDS) {
        return course.map { it.phase to it.seconds }
    }
    // Past MAX_WAVEFORM_ROUNDS, fold each block into its work and rest shares so the silhouette stays legible.
    fun total(phase: Phase, block: Int) = course.filter { it.phase == phase && it.block == block }.sumOf { it.seconds }
    return buildList {
        course.takeWhile { it.phase != Phase.Work }.forEach { add(it.phase to it.seconds) }
        config.blocks.indices.forEach { block ->
            add(Phase.Work to total(Phase.Work, block))
            if (total(Phase.Rest, block) > 0) add(Phase.Rest to total(Phase.Rest, block))
            course.filter { it.block == block && config.isBlockRest(it) }.forEach { add(Phase.Rest to it.seconds) }
        }
        val coolDown = course.filter { it.phase == Phase.CoolDown }.sumOf { it.seconds }
        if (coolDown > 0) add(Phase.CoolDown to coolDown)
    }
}

/**
 * The ground of a card that can be the current choice, such as a preset: the surface with a line
 * border, or the accent's tint and the accent itself once [selected], crossfading between the two.
 * Also tells screen readers.
 */
@Composable
fun Modifier.selectableCard(
    selected: Boolean,
    shape: Shape = Corners.radius18,
    announceSelection: Boolean = true,
): Modifier {
    val palette = IntervalTheme.palette
    val spec = tween<Color>(Motion.STATE_MILLIS, easing = StandardEasing)
    val fill by animateColorAsState(if (selected) palette.accentTint else palette.surface, spec, label = "fill")
    val line by animateColorAsState(if (selected) palette.accent else palette.line, spec, label = "line")
    return clip(shape)
        .drawBehind { drawRect(fill) }
        .border(Sizes.border, line, shape)
        .then(if (announceSelection) Modifier.semantics { this.selected = selected } else Modifier)
}

/** A value in a pill of its own colour, on a pale wash of it: "0:20" in work red, "×8" in accent. */
@Composable
fun ValuePill(
    text: String,
    color: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    height: Dp = IntervalSizes.valuePill,
    style: TextStyle = IntervalTheme.type.stat,
) {
    Box(
        modifier
            .heightIn(min = height)
            .clip(Corners.radius12)
            .background(tint)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space4),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = color)
    }
}

/** A [Timeline] at a list card's size: compact, and without the get-ready segment, which is the user's own. */
@Composable
fun MiniTimeline(config: IntervalConfig, modifier: Modifier = Modifier) {
    Timeline(config, modifier = modifier, height = Sizes.timelineMini, gap = Sizes.timelineMiniGap, showPrep = false)
}

private const val WORK_HEIGHT = 1.0f
private const val BOOKEND_HEIGHT = 0.56f
private const val REST_HEIGHT = 0.28f
private const val PREP_HEIGHT = 0.18f
private const val BOOKEND_ALPHA = 0.45f
private const val MAX_GAP_SHARE = 0.30f
private const val MIN_BAR_PX = 1.5f
private const val MAX_CORNER_PX = 4f

@PreviewFormFactors
@Composable
private fun TimelineWaveformPreview() {
    val tabata = IntervalConfig(workSeconds = 20, restSeconds = 10, rounds = 8)
    val mvo2 = IntervalConfig(workSeconds = 15, restSeconds = 15, rounds = 20)
    val sequence = IntervalConfig(
        workSeconds = 30,
        restSeconds = 15,
        rounds = 4,
        then = listOf(Block(workSeconds = 45, restSeconds = 15, rounds = 3)),
        blockRestSeconds = 60,
        warmUpSeconds = 120,
        coolDownSeconds = 120,
    )
    IntervalTheme {
        Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
            Timeline(tabata)
            MiniTimeline(tabata)
            Timeline(mvo2, showPrep = false)
            Timeline(sequence)
        }
    }
}
