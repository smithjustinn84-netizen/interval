package dev.justinsmith.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

/**
 * The streak as a chip: a flame and "3-week streak", worded by [label] with the module's own string. One
 * that [grew] with the workout just finished arrives a week short and rolls up to [weeks] after
 * [Motion.ENTER_MILLIS], the flame flaring as it does; Today's and Log's, which only report it, stand still.
 * On the workout's ground it takes that ground's [fill] and [ink].
 */
@Composable
fun StreakBadge(
    weeks: Int,
    label: @Composable (Int) -> String,
    modifier: Modifier = Modifier,
    grew: Boolean = false,
    fill: Color = IntervalTheme.palette.surfaceVariant,
    ink: Color = IntervalTheme.palette.text,
) {
    val palette = IntervalTheme.palette
    var shown by remember { mutableIntStateOf(if (grew && weeks > 1) weeks - 1 else weeks) }
    LaunchedEffect(weeks) {
        if (shown != weeks) {
            delay(Motion.ENTER_MILLIS.toLong())
            shown = weeks
        }
    }
    Row(
        modifier
            .clip(Corners.full)
            .background(fill)
            .padding(horizontal = Spacing.space12, vertical = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Icon(
            IntervalIcons.Streak,
            null,
            tint = palette.prepMark,
            modifier = Modifier.size(Sizes.chipIcon).popOnce(shown, Motion.FLAME_POP_SCALE),
        )
        RollingText(label(shown), rising = true, style = IntervalTheme.type.caption, color = ink)
    }
}
