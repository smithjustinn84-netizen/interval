package dev.justinsmith.interval.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A screen's top bar on one line: an optional navigation action (Back) first, the large title, and
 * the actions at the end, with no top padding of its own.
 *
 * The title is placed by its baseline, so its capitals start [Spacing.space8] under the status bar
 * instead of under the band of ascent the font keeps above them. The icon glyphs are drawn level
 * with the capitals, near the top of their 48dp touch targets, so the row's spare height falls
 * below the title rather than above it.
 *
 * A title with a word too wide to fit beside the actions (with large text, say, beside a wide one such as a
 * session's heart rate) takes a line of its own under them, the whole width, rather than breaking a letter or
 * two a line; so, with large text, does one that would wrap beside them and fits that line whole (Set up beside
 * Skip for now). At the usual size a title that wraps between words wraps beside them, as it always has.
 */
@Composable
fun TitleBar(
    title: String,
    modifier: Modifier = Modifier,
    navigation: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val style = IntervalTheme.type.title
    val capHeight = with(LocalDensity.current) { (style.fontSize * TITLE_CAP_HEIGHT_EM).toDp() }
    val capTop = Spacing.space8
    // From the touch target's centre to the capitals' centre: negative, so the glyphs move up.
    val glyphShift = capTop + capHeight / 2 - Sizes.iconButton / 2
    val large = isLargeText()
    CompositionLocalProvider(LocalTitleBarGlyphShift provides glyphShift) {
        Layout(
            contents = listOf(
                { Box { navigation() } },
                {
                    Text(
                        title,
                        style = style,
                        color = IntervalTheme.palette.text,
                        modifier = Modifier
                            .padding(start = Spacing.space12)
                            .capTopAt(capTop, capHeight)
                            .semantics { heading() },
                    )
                },
                { Row(verticalAlignment = Alignment.Top, content = actions) },
            ),
            modifier = modifier
                .fillMaxWidth()
                .padding(start = Spacing.space4, end = Spacing.space8, bottom = Spacing.space4),
        ) { (navigations, titles, actionRows), constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val nav = navigations.single().measure(loose)
            val acts = actionRows.single().measure(loose.copy(maxWidth = (loose.maxWidth - nav.width).coerceAtLeast(0)))
            val text = titles.single()
            val beside = (constraints.maxWidth - nav.width - acts.width).coerceAtLeast(0)
            // Beside the actions unless that breaks a word, or with large text wraps a title the whole width
            // would hold on one line. At the usual size a long title wraps beside Back, as it always has.
            val oneLine = text.maxIntrinsicWidth(Constraints.Infinity)
            val fits = text.minIntrinsicWidth(Constraints.Infinity) <= beside &&
                (!large || oneLine <= beside || oneLine > constraints.maxWidth)
            val head = maxOf(nav.height, acts.height)
            if (fits) {
                val placed = text.measure(Constraints(minWidth = beside, maxWidth = beside))
                val height = maxOf(head, placed.height)
                layout(constraints.maxWidth, height) {
                    nav.place(0, 0)
                    placed.place(nav.width, 0)
                    acts.place(constraints.maxWidth - acts.width, 0)
                }
            } else {
                val placed = text.measure(Constraints(maxWidth = constraints.maxWidth))
                layout(constraints.maxWidth, head + placed.height) {
                    nav.place(0, 0)
                    acts.place(constraints.maxWidth - acts.width, 0)
                    placed.place(0, head)
                }
            }
        }
    }
}

/** Barlow Condensed's cap height, from the font's OS/2 table: 700 of 1000 units. */
private const val TITLE_CAP_HEIGHT_EM = 0.7f

/** How far a [TitleBar] moves its icon glyphs from their touch target's centre; zero elsewhere. */
// Kept to this module (the sensor's pulse reads it too) and set only by TitleBar: the alternative is a
// parameter every action in every title bar must remember to pass, which is how one ends up misaligned.
@Suppress("detekt.CompositionLocalAllowlist")
internal val LocalTitleBarGlyphShift = staticCompositionLocalOf { 0.dp }

/**
 * Places text so its capitals start [capTop] below this node's top, from its first baseline. The
 * text may draw above its bounds; the node is as tall as what remains below that line.
 */
private fun Modifier.capTopAt(capTop: Dp, capHeight: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val baseline = placeable[FirstBaseline]
    val shift = if (baseline == AlignmentLine.Unspecified) 0 else (capTop + capHeight).roundToPx() - baseline
    layout(placeable.width, (placeable.height + shift).coerceAtLeast(0)) { placeable.place(0, shift) }
}

/**
 * An icon-only top-bar action, with its [description] for screen readers: the gear on Timer, + on
 * Presets, ⋯ on History. The whole 48dp box takes the touch; the circle, its press and its ripple
 * follow the glyph, which a [TitleBar] raises to its title. One that commits something (removes a
 * set, say) passes the [haptic] it answers with, as [surfaceButton] does.
 */
@Composable
fun IconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptic: HapticFeedbackType? = null,
    bordered: Boolean = false,
) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val chrome = if (bordered) {
        Modifier.background(palette.surface).border(Sizes.hairline, palette.line, CircleShape)
    } else {
        Modifier
    }
    Box(
        modifier
            .size(Sizes.iconButton)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptic?.let(haptics::performHapticFeedback)
                    onClick()
                },
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .offset(y = LocalTitleBarGlyphShift.current)
                .pressScale(interaction)
                .size(Sizes.iconButtonVisual)
                .clip(CircleShape)
                .then(chrome)
                .indication(interaction, LocalIndication.current),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) palette.text else palette.textDim.copy(alpha = Opacity.DISABLED),
                modifier = Modifier.size(Sizes.icon),
            )
        }
    }
}
