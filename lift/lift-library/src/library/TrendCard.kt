package dev.justinsmith.lift.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.Bar
import dev.justinsmith.interval.ui.Bars
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.domain.Trend
import dev.justinsmith.lift.domain.TrendMeasure
import dev.justinsmith.lift.domain.formatKg
import dev.justinsmith.lift.library.resources.Res
import dev.justinsmith.lift.library.resources.detail_trend
import dev.justinsmith.lift.library.resources.detail_trend_cd
import dev.justinsmith.lift.library.resources.detail_trend_kg
import dev.justinsmith.lift.library.resources.detail_trend_latest
import dev.justinsmith.lift.library.resources.detail_trend_reps
import dev.justinsmith.lift.library.resources.detail_trend_span
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The exercise's best each session as bars, oldest first, the latest filled: the weight, reps or hold as the
 * trend counts it. Under the records on the exercise's page.
 */
@Composable
internal fun TrendCard(trend: Trend, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val values = trend.points.map { trendValue(trend.measure, it.value) }
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
        Bars(
            bars = trend.points.mapIndexed { i, point ->
                Bar(point.value.toFloat(), filled = i == trend.points.lastIndex)
            },
            max = trend.max.toFloat(),
            description = stringResource(Res.string.detail_trend_cd, values.joinToString()),
        )
    }
}

/** A trend's value in its own words: "24 kg", "12 reps", "0:45". */
@Composable
private fun trendValue(measure: TrendMeasure, value: Double): String = when (measure) {
    TrendMeasure.Kg -> stringResource(Res.string.detail_trend_kg, formatKg(value))
    TrendMeasure.Reps -> pluralStringResource(Res.plurals.detail_trend_reps, value.toInt(), value.toInt())
    TrendMeasure.Seconds -> formatClock(value.toInt())
}
