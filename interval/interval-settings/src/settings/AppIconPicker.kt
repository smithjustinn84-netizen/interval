package dev.justinsmith.interval.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.platform.AppIcon
import dev.justinsmith.interval.settings.resources.Res
import dev.justinsmith.interval.settings.resources.app_icon_kettlebell
import dev.justinsmith.interval.settings.resources.settings_app_icon
import dev.justinsmith.interval.settings.resources.settings_app_icon_closes
import dev.justinsmith.interval.settings.resources.settings_app_icon_hint
import dev.justinsmith.interval.settings.resources.settings_app_icon_kettlebell
import dev.justinsmith.interval.settings.resources.settings_app_icon_ring
import dev.justinsmith.interval.splash.Mark
import dev.justinsmith.interval.splash.drawIntervalMark
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.PhaseColors
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.Squircle
import dev.justinsmith.interval.ui.Tilt
import dev.justinsmith.interval.ui.rememberTilt
import dev.justinsmith.interval.ui.sheen
import dev.justinsmith.interval.ui.tilting
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The app's icons to pick from, as a card's row: each as the Home Screen shows it, a squircle that leans under the
 * finger with a sheen sliding over it ([tilting], [sheen]), the one worn ([selected]) ringed in the accent and
 * ticked by its name. Where picking one closes the app ([closesApp], Android's way), a pill under them says so
 * before anything is picked; the screen still asks as one is. A radio group named by the row's title, each card
 * found and read by its icon's name.
 */
@Composable
internal fun AppIconPicker(
    selected: AppIcon,
    closesApp: Boolean,
    onSelect: (AppIcon) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(Res.string.settings_app_icon)
    Column(
        modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(title, style = IntervalTheme.type.label, color = IntervalTheme.palette.text)
            Text(
                stringResource(Res.string.settings_app_icon_hint),
                style = IntervalTheme.type.meta,
                color = IntervalTheme.palette.textDim,
            )
        }
        Row(
            Modifier.selectableGroup().semantics { contentDescription = title },
            horizontalArrangement = Arrangement.spacedBy(Spacing.space16),
        ) {
            AppIcon.entries.forEach { icon ->
                AppIconCard(icon, selected = icon == selected, onSelect = { onSelect(icon) })
            }
        }
        if (closesApp) ClosesAppPill()
    }
}

/** One icon as a card over its name: tapped, it is picked; pressed, it leans by [tilt]. */
@Composable
private fun AppIconCard(
    icon: AppIcon,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    tilt: Tilt = rememberTilt(),
) {
    val palette = IntervalTheme.palette
    val haptics = LocalHapticFeedback.current
    val ring by animateColorAsState(
        if (selected) palette.accent else Color.Transparent,
        tween(Motion.STATE_MILLIS),
        label = "iconRing",
    )
    val name by animateColorAsState(
        if (selected) palette.text else palette.textDim,
        tween(Motion.STATE_MILLIS),
        label = "iconName",
    )
    Column(
        modifier
            .clip(Corners.radius18)
            .selectable(selected = selected, role = Role.RadioButton) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSelect()
            }
            .padding(Spacing.space4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Box(
            Modifier
                .tilting(tilt)
                .border(IntervalSizes.appIconRing, ring, Squircle)
                .padding(IntervalSizes.appIconRing + IntervalSizes.appIconRingGap)
                .size(IntervalSizes.appIconCard)
                .clip(Squircle)
                .background(iconGround)
                .sheen(tilt),
        ) {
            IconArt(icon, Modifier.fillMaxSize())
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
        ) {
            if (selected) {
                Icon(
                    IntervalIcons.Check,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(IntervalSizes.appIconCheck),
                )
            }
            Text(stringResource(icon.labelRes), style = IntervalTheme.type.label, color = name)
        }
    }
}

/**
 * [icon]'s picture, the part of it a launcher shows: the rendered bell (`tools/generate_icons.py`'s copy of the
 * render's foreground), or the mark, drawn as the intro draws it.
 */
@Composable
private fun IconArt(icon: AppIcon, modifier: Modifier = Modifier) {
    when (icon) {
        AppIcon.Kettlebell -> Image(painterResource(Res.drawable.app_icon_kettlebell), null, modifier)
        AppIcon.Ring -> Canvas(modifier) { drawIntervalMark(center, unit = size.minDimension / LAUNCHER_CROP) }
    }
}

/** Says, before an icon is picked, that picking one closes the app: quiet, on the card, under the icons. */
@Composable
private fun ClosesAppPill(modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Row(
        modifier
            .clip(Corners.full)
            .background(palette.surfaceVariant)
            .padding(horizontal = Spacing.space12, vertical = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Box(Modifier.size(Sizes.coachDot).clip(Corners.full).background(palette.accent))
        Text(
            stringResource(Res.string.settings_app_icon_closes),
            style = IntervalTheme.type.caption,
            color = palette.textDim,
        )
    }
}

internal val AppIcon.labelRes: StringResource
    get() = when (this) {
        AppIcon.Kettlebell -> Res.string.settings_app_icon_kettlebell
        AppIcon.Ring -> Res.string.settings_app_icon_ring
    }

/**
 * The ground both icons stand on, as Android's launcher has it (`ic_launcher_background`): the workout's ground
 * with a glow at its middle, `generate_icons.py`'s `GROUND_GLOW`.
 */
private val iconGround = Brush.radialGradient(listOf(Color(0xFF1C3129), PhaseColors.ground))

/** Of the mark's 108 canvas, the middle a launcher shows of an adaptive icon. */
private const val LAUNCHER_CROP = Mark.CANVAS * 2 / 3

/** The picker as Android has it, the pill under it, then the cards leaning each way a finger would press them. */
@PreviewFormFactors
@Composable
private fun AppIconPickerPreview() {
    IntervalTheme {
        Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
            ListCard { AppIconPicker(AppIcon.Kettlebell, closesApp = true, onSelect = {}) }
            ListCard {
                Row(Modifier.padding(Spacing.space16), horizontalArrangement = Arrangement.spacedBy(Spacing.space16)) {
                    AppIconCard(AppIcon.Kettlebell, selected = false, onSelect = {
                    }, tilt = rememberTilt(Offset(1f, 1f)))
                    AppIconCard(AppIcon.Ring, selected = true, onSelect = {}, tilt = rememberTilt(Offset(-1f, -1f)))
                }
            }
        }
    }
}
