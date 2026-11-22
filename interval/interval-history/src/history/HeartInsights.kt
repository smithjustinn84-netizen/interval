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
import dev.justinsmith.interval.history.resources.common_joined
import dev.justinsmith.interval.history.resources.detail_heart_average_peak
import dev.justinsmith.interval.history.resources.detail_heart_load_value
import dev.justinsmith.interval.history.resources.detail_heart_recovery
import dev.justinsmith.interval.history.resources.detail_max_nudge
import dev.justinsmith.interval.history.resources.detail_max_use
import dev.justinsmith.interval.history.resources.detail_recovery_fading
import dev.justinsmith.interval.history.resources.detail_recovery_holding
import dev.justinsmith.interval.platform.Recovery
import dev.justinsmith.interval.platform.RecoveryTrend
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.JoinedText
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * What the heart rate says beyond the average a heart card reads out, as a line under the card: the [peak], how
 * far it came down in the rests ([recovery]) and what the whole cost ([load], minutes in each zone weighed by the
 * zone), each left out when there is none; then, when the last rest stood apart from the first, a line saying so.
 */
@Composable
internal fun HeartInsights(peak: Int?, recovery: Recovery?, load: Int?, modifier: Modifier = Modifier) {
    val parts = listOfNotNull(
        peak?.let { stringResource(Res.string.detail_heart_average_peak, it) },
        recovery?.let { stringResource(Res.string.detail_heart_recovery, it.average, it.seconds) },
        load?.let { stringResource(Res.string.detail_heart_load_value, it) },
    )
    if (parts.isEmpty()) return
    val palette = IntervalTheme.palette
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.space4),
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        JoinedText(
            parts.reduce { line, part -> stringResource(Res.string.common_joined, line, part) },
            style = IntervalTheme.type.meta,
            color = palette.textDim,
        )
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
                color = palette.textDim,
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
            HeartInsights(peak = 171, recovery = holding, load = 86)
            HeartInsights(peak = 182, recovery = fading, load = 112)
            MaxNudge(peak = 194, max = 190, onUse = {})
        }
    }
}
