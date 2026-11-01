package dev.justinsmith.lift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Opacity
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.domain.MuscleBalance
import dev.justinsmith.lift.domain.MuscleGroup
import dev.justinsmith.lift.domain.MuscleLoad
import dev.justinsmith.lift.ui.resources.Res
import dev.justinsmith.lift.ui.resources.balance_assisted
import dev.justinsmith.lift.ui.resources.balance_joined
import dev.justinsmith.lift.ui.resources.balance_row_cd
import dev.justinsmith.lift.ui.resources.balance_title
import dev.justinsmith.lift.ui.resources.balance_worked
import dev.justinsmith.lift.ui.resources.muscle_group_arms
import dev.justinsmith.lift.ui.resources.muscle_group_back
import dev.justinsmith.lift.ui.resources.muscle_group_chest
import dev.justinsmith.lift.ui.resources.muscle_group_core
import dev.justinsmith.lift.ui.resources.muscle_group_hips
import dev.justinsmith.lift.ui.resources.muscle_group_legs
import dev.justinsmith.lift.ui.resources.muscle_group_shoulders
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Sets per muscle group, a bar each in a fixed order so a gap shows where it always is: what worked a group most
 * in the accent, what only assisted it lighter after. Headed Balance and [span], what the sets are of (the last
 * four weeks, a week of a program); a group nothing trained is named under the bars in [untrained], the one line
 * of advice they give. The caller gives them their card.
 */
@Composable
fun BalanceBars(
    balance: MuscleBalance,
    span: StringResource,
    untrained: StringResource,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.balance_title),
                style = IntervalTheme.type.caption,
                color = palette.textDim,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(span), style = IntervalTheme.type.meta, color = palette.textDim)
        }
        balance.loads.forEach { load -> LoadRow(load, balance.max) }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space16)) {
            Legend(palette.accent, stringResource(Res.string.balance_worked))
            Legend(palette.accent.copy(alpha = Opacity.LIGHT_SHARE), stringResource(Res.string.balance_assisted))
        }
        if (balance.untrained.isNotEmpty()) {
            val names = balance.untrained.map { groupName(it) }
                .reduce { joined, name -> stringResource(Res.string.balance_joined, joined, name) }
            Text(stringResource(untrained, names), style = IntervalTheme.type.meta, color = palette.text)
        }
    }
}

/** A group's name and the sets that worked it most over its bar, read as one line. */
@Composable
private fun LoadRow(load: MuscleLoad, max: Double) {
    val palette = IntervalTheme.palette
    val name = groupName(load.group)
    val worked = load.worked.roundToInt()
    val description = pluralStringResource(
        Res.plurals.balance_row_cd,
        worked,
        name,
        worked,
        load.assisted.roundToInt(),
    )
    Column(
        Modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = IntervalTheme.type.meta, color = palette.text, modifier = Modifier.weight(1f))
            Text(worked.toString(), style = IntervalTheme.type.meta, color = palette.textDim)
        }
        val track = palette.surfaceVariant
        val accent = palette.accent
        Box(
            Modifier
                .fillMaxWidth()
                .height(LiftSizes.balanceBar)
                .drawBehind {
                    val top = max.coerceAtLeast(1.0)
                    share(1.0, track)
                    share(load.total / top, accent.copy(alpha = Opacity.LIGHT_SHARE))
                    share(load.worked / top, accent)
                },
        )
    }
}

/** The bar's first [fraction] of its width, rounded at both ends as the track is. Nothing for none. */
private fun DrawScope.share(fraction: Double, color: Color) {
    if (fraction <= 0.0) return
    val width = (size.width * fraction.toFloat()).coerceAtLeast(size.height)
    drawRoundRect(color, size = Size(width, size.height), cornerRadius = CornerRadius(size.height / 2))
}

@Composable
private fun groupName(group: MuscleGroup): String = stringResource(
    when (group) {
        MuscleGroup.Chest -> Res.string.muscle_group_chest
        MuscleGroup.Shoulders -> Res.string.muscle_group_shoulders
        MuscleGroup.Back -> Res.string.muscle_group_back
        MuscleGroup.Arms -> Res.string.muscle_group_arms
        MuscleGroup.Core -> Res.string.muscle_group_core
        MuscleGroup.Hips -> Res.string.muscle_group_hips
        MuscleGroup.Legs -> Res.string.muscle_group_legs
    },
)

/** A swatch of [color] and what it stands for, under a chart. */
@Composable
fun Legend(color: Color, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        Box(Modifier.size(Sizes.legendSwatch).clip(Corners.full).background(color))
        Text(text, style = IntervalTheme.type.caption, color = IntervalTheme.palette.textDim)
    }
}
