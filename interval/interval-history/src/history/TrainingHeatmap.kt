package dev.justinsmith.interval.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.history_heatmap_cd
import dev.justinsmith.interval.history.resources.history_heatmap_day_cd
import dev.justinsmith.interval.history.resources.history_heatmap_title
import dev.justinsmith.interval.history.resources.history_kind_conditioning
import dev.justinsmith.interval.history.resources.history_kind_mobility
import dev.justinsmith.interval.history.resources.history_kind_sport
import dev.justinsmith.interval.history.resources.history_kind_strength
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.DAYS_PER_WEEK
import dev.justinsmith.interval.timer.weekStartOf
import dev.justinsmith.interval.timer.weekdayOf
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.WeekdayForm
import dev.justinsmith.interval.ui.weekdayText
import org.jetbrains.compose.resources.stringResource

/** One day on the 12-week training heatmap: its duration split by pillar, whether a sport was logged, and its date. */
data class HeatmapDay(
    val epochDay: Long,
    /** 1 for Monday through 7 for Sunday. */
    val weekday: Int,
    val liftSeconds: Int = 0,
    val intervalSeconds: Int = 0,
    val mobilitySeconds: Int = 0,
    val sportLogged: Boolean = false,
    val today: Boolean = false,
    val future: Boolean = false,
    val date: String = "",
) {
    val totalSeconds: Int get() = liftSeconds + intervalSeconds + mobilitySeconds
    val active: Boolean get() = totalSeconds > 0 || sportLogged
}

/**
 * Builds the [weeks] full weeks (Monday–Sunday, oldest first) ending on the current week's Sunday for [today],
 * reading each day's pillar breakdown from [log].
 */
internal fun heatmapDays(
    log: TrainingLog,
    today: Long,
    weeks: Int,
    dateOf: (Long) -> String = { "" },
): List<HeatmapDay> {
    if (weeks <= 0) return emptyList()
    val thisMonday = weekStartOf(today)
    val firstDay = thisMonday - (weeks - 1).toLong() * DAYS_PER_WEEK
    val totalDays = weeks * DAYS_PER_WEEK
    return List(totalDays) { offset ->
        val epochDay = firstDay + offset
        val day = log.day(epochDay)
        val lift = day.liftWorkouts.sumOf { it.elapsedSeconds }
        val mobility = day.workouts.filter { it.config.activity == Activity.Mobility }.sumOf { it.elapsedSeconds }
        val interval = day.workouts.filter { it.config.activity != Activity.Mobility }.sumOf { it.elapsedSeconds }
        HeatmapDay(
            epochDay = epochDay,
            weekday = weekdayOf(epochDay),
            liftSeconds = lift,
            intervalSeconds = interval,
            mobilitySeconds = mobility,
            sportLogged = day.sports.isNotEmpty(),
            today = epochDay == today,
            future = epochDay > today,
            date = dateOf(epochDay),
        )
    }
}

/**
 * 12-week × 7-day multi-pillar training consistency heatmap: each tile is shaded by its pillar(s) and duration
 * intensity, with multi-pillar days cleanly split. Tapping any past or current day opens it via [onDay].
 */
@Composable
internal fun TrainingHeatmap(
    days: List<HeatmapDay>,
    onDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return
    val weeks = days.chunked(DAYS_PER_WEEK)
    val maxSeconds = days.maxOfOrNull { it.totalSeconds }?.coerceAtLeast(1) ?: 1
    val palette = IntervalTheme.palette
    val workout = IntervalTheme.workout
    val activeCount = days.count { it.active }
    val summaryCd = stringResource(Res.string.history_heatmap_cd, activeCount, weeks.size)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        Text(
            stringResource(Res.string.history_heatmap_title),
            style = IntervalTheme.type.caption,
            color = palette.textDim,
        )
        Column(
            Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = summaryCd },
            verticalArrangement = Arrangement.spacedBy(TILE_GAP),
        ) {
            for (rowIndex in 0..<DAYS_PER_WEEK) {
                val weekday = rowIndex + 1
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(WEEKDAY_LABEL_WIDTH), contentAlignment = Alignment.CenterStart) {
                        if (weekday in LABELED_WEEKDAYS) {
                            Text(
                                weekdayText(weekday, WeekdayForm.Initial),
                                style = IntervalTheme.type.caption,
                                color = palette.textDim,
                            )
                        }
                    }
                    weeks.forEach { week ->
                        HeatmapTile(
                            day = week[rowIndex],
                            maxSeconds = maxSeconds,
                            strength = palette.accent,
                            conditioning = workout.work,
                            mobility = workout.rest,
                            empty = palette.surfaceVariant,
                            onDay = onDay,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        HeatmapLegend(palette.accent, workout.work, workout.rest)
    }
}

@Composable
private fun HeatmapTile(
    day: HeatmapDay,
    maxSeconds: Int,
    strength: Color,
    conditioning: Color,
    mobility: Color,
    empty: Color,
    onDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = tileColors(day, maxSeconds, strength, conditioning, mobility, empty)
    val dayCd = if (day.date.isNotBlank()) stringResource(Res.string.history_heatmap_day_cd, day.date) else ""
    val clickable = if (!day.future) {
        Modifier.clickable(role = Role.Button, onClickLabel = dayCd.ifBlank { null }) { onDay(day.epochDay) }
    } else {
        Modifier
    }
    val todayRing = if (day.today) {
        Modifier.border(TODAY_BORDER, strength, TileShape)
    } else {
        Modifier
    }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(TileShape)
            .drawWithCache {
                // Diagonal split for multi-pillar training days: its lower half built once a size, not each draw.
                val lower = if (colors.size > 1) {
                    Path().apply {
                        moveTo(size.width, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                } else {
                    null
                }
                onDrawBehind {
                    drawRect(colors[0])
                    if (lower != null) drawPath(lower, color = colors[1])
                }
            }
            .then(todayRing)
            .then(clickable),
    )
}

private fun tileColors(
    day: HeatmapDay,
    maxSeconds: Int,
    strength: Color,
    conditioning: Color,
    mobility: Color,
    empty: Color,
): List<Color> {
    if (day.future) return listOf(empty.copy(alpha = FUTURE_ALPHA))
    if (!day.active) return listOf(empty)
    val intensity = if (day.totalSeconds > 0) {
        MIN_ACTIVE_ALPHA + (1f - MIN_ACTIVE_ALPHA) * (day.totalSeconds.toFloat() / maxSeconds).coerceIn(0f, 1f)
    } else {
        SPORT_ONLY_ALPHA
    }
    val pillars = buildList {
        if (day.liftSeconds > 0 || day.sportLogged) add(strength.copy(alpha = intensity))
        if (day.intervalSeconds > 0) add(conditioning.copy(alpha = intensity))
        if (day.mobilitySeconds > 0) add(mobility.copy(alpha = intensity))
    }
    return pillars.ifEmpty { listOf(empty) }
}

@Composable
private fun HeatmapLegend(strength: Color, conditioning: Color, mobility: Color) {
    val palette = IntervalTheme.palette
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        LegendDot(strength, stringResource(Res.string.history_kind_strength), palette.textDim)
        LegendDot(conditioning, stringResource(Res.string.history_kind_conditioning), palette.textDim)
        LegendDot(mobility, stringResource(Res.string.history_kind_mobility), palette.textDim)
        LegendDot(
            strength.copy(alpha = SPORT_ONLY_ALPHA),
            stringResource(Res.string.history_kind_sport),
            palette.textDim,
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String, textColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        Box(Modifier.size(Sizes.legendSwatch).clip(Corners.full).background(color))
        Text(label, style = IntervalTheme.type.caption, color = textColor)
    }
}

private val LABELED_WEEKDAYS = setOf(1, 3, 5)
private val TileShape = RoundedCornerShape(4.dp)
private val TILE_GAP = 3.dp
private val WEEKDAY_LABEL_WIDTH = 16.dp
private val TODAY_BORDER = 1.5.dp
private const val FUTURE_ALPHA = 0.35f
private const val MIN_ACTIVE_ALPHA = 0.55f
private const val SPORT_ONLY_ALPHA = 0.72f

private val previewHeatmap = List(12 * DAYS_PER_WEEK) { i ->
    val day = i.toLong()
    val weekday = (i % DAYS_PER_WEEK) + 1
    HeatmapDay(
        epochDay = day,
        weekday = weekday,
        liftSeconds = if (weekday == 1 || weekday == 4) 1800 else 0,
        intervalSeconds = if (weekday == 2 || (weekday == 4 && i % 2 == 0)) 1200 else 0,
        mobilitySeconds = if (weekday == 6) 900 else 0,
        sportLogged = weekday == 3 && i % 3 != 0,
        today = i == 12 * DAYS_PER_WEEK - 3,
        future = i > 12 * DAYS_PER_WEEK - 3,
    )
}

@PreviewFormFactors
@Composable
private fun TrainingHeatmapPreview() {
    IntervalTheme {
        ListCard(Modifier.padding(Spacing.space16)) {
            TrainingHeatmap(previewHeatmap, onDay = {}, modifier = Modifier.padding(Spacing.space16))
        }
    }
}
