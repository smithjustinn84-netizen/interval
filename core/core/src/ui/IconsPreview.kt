package dev.justinsmith.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private val previewIcons: List<Pair<String, ImageVector>> = listOf(
    "KbSwing" to IntervalIcons.KbSwing,
    "KbClean" to IntervalIcons.KbClean,
    "KbSnatch" to IntervalIcons.KbSnatch,
    "KbPress" to IntervalIcons.KbPress,
    "TurkishGetUp" to IntervalIcons.TurkishGetUp,
    "TimerEmom" to IntervalIcons.TimerEmom,
    "TimerTabata" to IntervalIcons.TimerTabata,
    "TimerAmrap" to IntervalIcons.TimerAmrap,
)

private val PREVIEW_LARGE_ICON = 48.dp

/** Visual showcase of the kettlebell movement and timer mode [IntervalIcons] at large, standard, and list-row sizes. */
@OptIn(ExperimentalLayoutApi::class)
@Preview
@Composable
private fun IntervalIconsPreview() {
    IntervalTheme {
        val palette = IntervalTheme.palette
        Column(
            Modifier
                .fillMaxSize()
                .background(palette.background)
                .padding(Spacing.space16),
            verticalArrangement = Arrangement.spacedBy(Spacing.space16),
        ) {
            GroupLabel("Movements & timer modes")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
                verticalArrangement = Arrangement.spacedBy(Spacing.space8),
            ) {
                previewIcons.forEach { (label, icon) ->
                    Column(
                        Modifier
                            .clip(Corners.radius12)
                            .background(palette.surface)
                            .border(Sizes.hairline, palette.line, Corners.radius12)
                            .padding(Spacing.space12),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = palette.text,
                            modifier = Modifier.size(PREVIEW_LARGE_ICON),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(Sizes.icon),
                            )
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = palette.textDim,
                                modifier = Modifier.size(Sizes.iconInline),
                            )
                        }
                        Text(label, style = IntervalTheme.type.caption, color = palette.textDim)
                    }
                }
            }
            GroupLabel("List row scale")
            ListCard {
                previewIcons.forEachIndexed { index, (label, icon) ->
                    if (index > 0) RowDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.space16, vertical = Spacing.space8),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = palette.textDim,
                            modifier = Modifier.size(Sizes.icon),
                        )
                        Text(label, style = IntervalTheme.type.label, color = palette.text)
                    }
                }
            }
        }
    }
}
