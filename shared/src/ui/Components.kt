package dev.justinsmith.interval.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.Thumb
import com.composeunstyled.Track
import com.composeunstyled.UnstyledSwitch
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.timer_start_workout
import dev.justinsmith.interval.timer.IntervalConfig
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Round-by-round marks (the workout's dots, a timeline's segments) stop being readable past a
 * dozen rounds; beyond that the text carries the count and a timeline shows only the shares.
 */
const val MAX_ROUND_MARKS = 12

/**
 * A screen's top bar on one line: an optional navigation action (Back) first, the large title, and
 * the actions at the end, with no top padding of its own.
 *
 * The title is placed by its baseline, so its capitals start [Spacing.space8] under the status bar
 * instead of under the band of ascent the font keeps above them. The icon glyphs are drawn level
 * with the capitals, near the top of their 48dp touch targets, so the row's spare height falls
 * below the title rather than above it.
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
    CompositionLocalProvider(LocalTitleBarGlyphShift provides glyphShift) {
        Row(
            modifier
                .fillMaxWidth()
                .padding(start = Spacing.space4, end = Spacing.space8, bottom = Spacing.space4),
            verticalAlignment = Alignment.Top,
        ) {
            navigation()
            Text(
                title,
                style = style,
                color = IntervalTheme.palette.text,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.space12)
                    .capTopAt(capTop, capHeight)
                    .semantics { heading() },
            )
            actions()
        }
    }
}

/** Barlow Condensed's cap height, from the font's OS/2 table: 700 of 1000 units. */
private const val TITLE_CAP_HEIGHT_EM = 0.7f

/** How far a [TitleBar] moves its icon glyphs from their touch target's centre; zero elsewhere. */
// Private to this file and set only by TitleBar: the alternative is a parameter every action in
// every title bar must remember to pass, which is how one ends up misaligned.
@Suppress("detekt.CompositionLocalAllowlist")
private val LocalTitleBarGlyphShift = staticCompositionLocalOf { 0.dp }

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
 * Presets, ⋯ on History. A [background] can fill its circle for an action a screen wants noticed;
 * none does today. The whole 48dp box takes the touch; the circle and its ripple follow the glyph,
 * which a [TitleBar] raises to its title.
 */
@Composable
fun IconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    background: Color = Color.Transparent,
    tint: Color = IntervalTheme.palette.text,
) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(Sizes.iconButton)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .offset(y = LocalTitleBarGlyphShift.current)
                .size(Sizes.iconButtonVisual)
                .clip(CircleShape)
                .background(if (enabled) background else Color.Transparent)
                .indication(interaction, LocalIndication.current),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) tint else palette.textDim.copy(alpha = Opacity.DISABLED),
                modifier = Modifier.size(Sizes.icon),
            )
        }
    }
}

/** The label over a group of list rows: "Built-in", "Today", "Cues". */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = IntervalTheme.type.label,
        color = IntervalTheme.palette.textDim,
        modifier = modifier
            .padding(start = Spacing.space4, top = Spacing.space12, bottom = Spacing.space8)
            .semantics { heading() },
    )
}

/** A white card holding a group of rows, separated by [RowDivider]s. */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Corners.radius18)
            .background(IntervalTheme.palette.surface),
        content = content,
    )
}

/** The hairline between rows in a [ListCard], inset from the leading edge. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier.padding(start = Spacing.space16), color = IntervalTheme.palette.line)
}

/**
 * A row that leads to another screen: a [title] with an optional [subtitle], and a chevron saying so.
 * The row itself is the button; the chevron is drawn, not read.
 */
@Composable
fun LinkRow(title: String, subtitle: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .clickable(interaction, LocalIndication.current, role = Role.Button, onClick = onClick)
            // After the ripple, so the content shrinks while the ripple keeps the card's full width.
            .pressScale(interaction)
            .padding(start = Spacing.space16, end = Spacing.space12, top = Spacing.space8, bottom = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = IntervalTheme.type.label, color = palette.text)
            if (subtitle != null) Text(subtitle, style = IntervalTheme.type.meta, color = palette.textDim)
        }
        Icon(
            IntervalIcons.Chevron,
            contentDescription = null,
            tint = palette.textDim,
            modifier = Modifier.size(Sizes.icon),
        )
    }
}

/**
 * What a list shows before it has anything: an [icon] in a ring of the accent's tint, a [title] and
 * a line of [text] saying what will appear, centred in whatever card holds it.
 */
@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.space24, vertical = Spacing.space32),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        Box(
            Modifier
                .padding(bottom = Spacing.space12)
                .size(Sizes.emptyIconRing)
                .clip(CircleShape)
                .background(palette.accentTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(Sizes.emptyIcon))
        }
        Text(title, style = IntervalTheme.type.section, color = palette.text, textAlign = TextAlign.Center)
        Text(text, style = IntervalTheme.type.body, color = palette.textDim, textAlign = TextAlign.Center)
    }
}

/**
 * A row that switches a setting: a [title], an optional [subtitle] and a switch drawing [checked].
 * When not [enabled] (a setting that cannot apply right now) the whole row dims and takes no touch.
 */
@Composable
fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = IntervalTheme.palette
    val haptics = LocalHapticFeedback.current
    // The track's colour follows the thumb across, rather than jumping ahead of it.
    val track by animateColorAsState(
        if (checked) palette.accent else palette.textDim,
        tween(Motion.STATE_MILLIS, easing = StandardEasing),
        label = "track",
    )
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Switch,
            ) { on ->
                haptics.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onToggle()
            }
            // After the ripple, so the content shrinks while the ripple keeps the card's full width.
            .pressScale(interaction)
            .alpha(if (enabled) 1f else Opacity.DISABLED)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = IntervalTheme.type.label, color = palette.text)
            if (subtitle != null) Text(subtitle, style = IntervalTheme.type.meta, color = palette.textDim)
        }
        // The row owns the toggle and its semantics; the switch only draws the state. One white thumb
        // in both states, the track's accent or text-dim saying which, as the design system draws it.
        UnstyledSwitch(checked = checked, onCheckedChange = null) {
            Track(
                Modifier
                    .size(Sizes.switchWidth, Sizes.switchHeight)
                    .clip(Corners.full)
                    .background(track)
                    .padding((Sizes.switchHeight - Sizes.switchThumb) / 2),
            ) {
                Thumb(
                    Modifier
                        .size(Sizes.switchThumb)
                        .clip(Corners.full)
                        .background(palette.onAccent),
                )
            }
        }
    }
}

/**
 * The one raised, accent-filled action of a screen: Start workout. Its shadow is the accent itself,
 * so the button glows rather than sits in a grey smudge.
 */
@Composable
fun StartButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val shape = Corners.radius18
    val glow = palette.accent.copy(alpha = Opacity.START_SHADOW)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(Sizes.start)
            .pressScale(interaction)
            .shadow(elevation = Sizes.startElevation, shape = shape, ambientColor = glow, spotColor = glow)
            .clip(shape)
            .background(palette.accent)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button) {
                onClick()
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            IntervalIcons.Play,
            contentDescription = null,
            tint = palette.onAccent,
            modifier = Modifier.size(Sizes.playIcon),
        )
        Text(
            stringResource(Res.string.timer_start_workout),
            style = IntervalTheme.type.value,
            color = palette.onAccent,
        )
    }
}

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

/**
 * A stepper button that repeats while held: one step on tap, then a fast run after a short hold.
 * Every step ticks under the thumb, so a held run can be counted without looking.
 */
@Composable
fun RepeatButton(
    symbol: String,
    description: String,
    enabled: Boolean,
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val step by rememberUpdatedState {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        onStep()
    }
    // Set once the hold starts repeating, so the click that ends the hold doesn't add one more step.
    // Cleared on every new press or cancelled press: a hold that ends without a click (dragged off,
    // or the field hit its limit) must not swallow the next tap.
    var repeated by remember { mutableStateOf(false) }
    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            if (it is PressInteraction.Press || it is PressInteraction.Cancel) repeated = false
        }
    }
    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled) {
            delay(Motion.STEPPER_HOLD_MILLIS)
            repeated = true
            while (true) {
                step()
                delay(Motion.STEPPER_REPEAT_MILLIS)
            }
        }
    }
    Box(
        modifier
            .size(Sizes.stepper)
            .pressScale(interaction)
            .clip(Corners.radius12)
            .background(palette.surfaceVariant)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
            ) {
                if (!repeated) step()
                repeated = false
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            style = IntervalTheme.type.control,
            color = if (enabled) palette.text else palette.textDim.copy(alpha = Opacity.DISABLED_GLYPH),
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
