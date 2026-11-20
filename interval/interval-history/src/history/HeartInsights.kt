package dev.justinsmith.interval.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.detail_bpm_value
import dev.justinsmith.interval.history.resources.detail_heart_load
import dev.justinsmith.interval.history.resources.detail_max_nudge
import dev.justinsmith.interval.history.resources.detail_max_use
import dev.justinsmith.interval.history.resources.detail_recovery_fading
import dev.justinsmith.interval.history.resources.detail_recovery_holding
import dev.justinsmith.interval.history.resources.detail_recovery_in
import dev.justinsmith.interval.platform.Recovery
import dev.justinsmith.interval.platform.RecoveryTrend
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * What the heart rate says of a workout beyond its average and peak: how far it came down in the rests
 * ([recovery], with a line when the last rest stood apart from the first) and what the whole cost ([load],
 * minutes in each zone weighed by the zone). Each is left out when the workout has none.
 */
@Composable
internal fun HeartInsights(recovery: Recovery?, load: Int?, modifier: Modifier = Modifier) {
    if (recovery == null && load == null) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            recovery?.let {
                Stat(
                    stringResource(Res.string.detail_recovery_in, it.seconds),
                    stringResource(Res.string.detail_bpm_value, it.average),
                    accent = true,
                )
            }
            load?.let { Stat(stringResource(Res.string.detail_heart_load), it.toString()) }
        }
        recovery?.trend?.let { trend ->
            Text(
                when (trend) {
                    RecoveryTrend.Holding -> stringResource(Res.string.detail_recovery_holding)

                    RecoveryTrend.Fading -> stringResource(
                        Res.string.detail_recovery_fading,
                        recovery.drops.first(),
                        recovery.drops.last(),
                    )
                },
                style = IntervalTheme.type.meta,
                color = IntervalTheme.palette.textDim,
            )
        }
    }
}

/**
 * The workout's heart rate went over the max the zones go by, so every zone was read too high: [peak] is
 * offered in its place, one tap.
 */
@Composable
internal fun MaxNudge(peak: Int, max: Int, onUse: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .clip(Corners.radius12)
            .background(palette.surfaceVariant)
            .padding(start = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Text(
            stringResource(Res.string.detail_max_nudge, peak, max),
            style = IntervalTheme.type.meta,
            color = palette.text,
            modifier = Modifier.weight(1f).padding(vertical = Spacing.space8),
        )
        QuietTextButton(stringResource(Res.string.detail_max_use, peak), onUse)
    }
}

@PreviewFormFactors
@Composable
private fun HeartInsightsPreview() {
    val holding = Recovery(listOf(34, 31, 29, 30), seconds = 60)
    val fading = Recovery(listOf(34, 28, 22, 19), seconds = 60)
    IntervalTheme {
        Column(
            Modifier.background(IntervalTheme.palette.surface).padding(Spacing.space16),
            verticalArrangement = Arrangement.spacedBy(Spacing.space16),
        ) {
            HeartInsights(holding, load = 86)
            HeartInsights(fading, load = 112)
            MaxNudge(peak = 194, max = 190, onUse = {})
        }
    }
}
