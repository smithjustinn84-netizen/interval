package dev.justinsmith.lift.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.domain.Trend
import dev.justinsmith.lift.domain.TrendMeasure
import dev.justinsmith.lift.domain.TrendPoint
import dev.justinsmith.lift.domain.formatKg
import dev.justinsmith.lift.library.resources.Res
import dev.justinsmith.lift.library.resources.detail_trend
import dev.justinsmith.lift.library.resources.detail_trend_bell
import dev.justinsmith.lift.library.resources.detail_trend_cd
import dev.justinsmith.lift.library.resources.detail_trend_from
import dev.justinsmith.lift.library.resources.detail_trend_from_bells
import dev.justinsmith.lift.library.resources.detail_trend_latest
import dev.justinsmith.lift.library.resources.detail_trend_reps
import dev.justinsmith.lift.library.resources.detail_trend_span
import dev.justinsmith.lift.library.resources.detail_trend_work
import dev.justinsmith.lift.ui.Sparkline
import dev.justinsmith.lift.ui.SparklineLabel
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The exercise's best each session as a line, oldest first, the latest in the accent: the best set's weight times
 * reps, the reps or the hold as the trend counts it, each bell's stretch scaled on its own, named over its start with
 * the latest at that bell ("16 kg × 12", or "16 kg" where that won't fit) and marked where it begins, with where it
 * started under it. Under the records on the exercise's page.
 */
@Composable
internal fun TrendCard(trend: Trend, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val values = trend.points.map { trendValue(trend.measure, it) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        GroupLabel(stringResource(Res.string.detail_trend))
        Row(Modifier.fillMaxWidth()) {
            Text(
                pluralStringResource(Res.plurals.detail_trend_span, trend.points.size, trend.points.size),
                style = IntervalTheme.type.caption,
                color = palette.textDim,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(Res.string.detail_trend_latest, values.last()),
                style = IntervalTheme.type.caption,
                color = palette.accent,
            )
        }
        Sparkline(
            heights = trend.heights.map { it.toFloat() },
            description = stringResource(Res.string.detail_trend_cd, values.joinToString()),
            marks = trend.bellChanges,
            labels = trend.latestAtBells.map {
                SparklineLabel(
                    text = stringResource(Res.string.detail_trend_work, formatKg(it.kg), it.reps),
                    short = stringResource(Res.string.detail_trend_bell, formatKg(it.kg)),
                )
            },
        )
        Text(
            stringResource(
                if (trend.bellChanges.isEmpty()) Res.string.detail_trend_from else Res.string.detail_trend_from_bells,
                values.first(),
            ),
            style = IntervalTheme.type.caption,
            color = palette.textDim,
        )
    }
}

/** A trend's point in its own words: "24 kg × 12", "12 reps", "0:45". */
@Composable
private fun trendValue(measure: TrendMeasure, point: TrendPoint): String = when (measure) {
    TrendMeasure.Work -> stringResource(Res.string.detail_trend_work, formatKg(point.kg), point.reps)
    TrendMeasure.Reps -> pluralStringResource(Res.plurals.detail_trend_reps, point.value.toInt(), point.value.toInt())
    TrendMeasure.Seconds -> formatClock(point.value.toInt())
}
