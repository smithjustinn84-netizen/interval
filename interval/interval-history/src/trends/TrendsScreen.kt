package dev.justinsmith.interval.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.justinsmith.interval.history.SummaryCard
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.history_since
import dev.justinsmith.interval.history.resources.trends_bars_cd
import dev.justinsmith.interval.history.resources.trends_close_cd
import dev.justinsmith.interval.history.resources.trends_empty
import dev.justinsmith.interval.history.resources.trends_heart_hint
import dev.justinsmith.interval.history.resources.trends_heart_title
import dev.justinsmith.interval.history.resources.trends_lifted_title
import dev.justinsmith.interval.history.resources.trends_records_count
import dev.justinsmith.interval.history.resources.trends_records_title
import dev.justinsmith.interval.history.resources.trends_recovery_bpm
import dev.justinsmith.interval.history.resources.trends_recovery_cd
import dev.justinsmith.interval.history.resources.trends_recovery_faster
import dev.justinsmith.interval.history.resources.trends_recovery_hint
import dev.justinsmith.interval.history.resources.trends_recovery_range
import dev.justinsmith.interval.history.resources.trends_recovery_slower
import dev.justinsmith.interval.history.resources.trends_recovery_steady
import dev.justinsmith.interval.history.resources.trends_recovery_title
import dev.justinsmith.interval.history.resources.trends_test_cd
import dev.justinsmith.interval.history.resources.trends_test_change
import dev.justinsmith.interval.history.resources.trends_tests_title
import dev.justinsmith.interval.history.resources.trends_this_week
import dev.justinsmith.interval.history.resources.trends_this_week_usual
import dev.justinsmith.interval.history.resources.trends_time_title
import dev.justinsmith.interval.history.resources.trends_title
import dev.justinsmith.interval.history.timeSpent
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.Bar
import dev.justinsmith.interval.ui.Bars
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.ScrollPage
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.PersonalRecord
import dev.justinsmith.lift.domain.TestId
import dev.justinsmith.lift.domain.strengthTest
import dev.justinsmith.lift.ui.Sparkline
import dev.justinsmith.lift.ui.liftName
import dev.justinsmith.lift.ui.recordLine
import dev.justinsmith.lift.ui.testCount
import dev.justinsmith.lift.ui.testName
import dev.justinsmith.lift.ui.testResultLine
import dev.justinsmith.lift.ui.tonnageValue
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/** Resolves the page's ViewModel and binds it to [TrendsScreen]. [inPane] when it sits beside the Log. */
@Composable
fun TrendsRoute(onBack: () -> Unit, modifier: Modifier = Modifier, inPane: Boolean = false) {
    val viewModel = koinViewModel<TrendsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    TrendsScreen(state, onBack, modifier, inPane)
}

/**
 * How training has gone over the last twelve weeks, from both sides' logs: the time a week, the weight lifted and
 * the heart's load, each against a usual week; how fast the heart comes down after work; the records set, newest
 * first; and each test's results at one bell. A card with nothing to draw is left out.
 */
@Composable
fun TrendsScreen(state: TrendsUiState, onBack: () -> Unit, modifier: Modifier = Modifier, inPane: Boolean = false) {
    val palette = IntervalTheme.palette
    ScrollPage(
        stringResource(Res.string.trends_title),
        modifier,
        navigation = { if (!inPane) BackAction(onBack) },
        actions = {
            if (inPane) IconAction(IntervalIcons.Close, stringResource(Res.string.trends_close_cd), onBack)
        },
    ) {
        if (state.loading) return@ScrollPage
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
            if (state.trends.empty) {
                Text(
                    stringResource(Res.string.trends_empty),
                    style = IntervalTheme.type.body,
                    color = palette.textDim,
                    modifier = Modifier.padding(horizontal = Spacing.space4),
                )
            } else {
                Text(
                    stringResource(Res.string.history_since, state.since),
                    style = IntervalTheme.type.label,
                    color = palette.textDim,
                    modifier = Modifier.padding(horizontal = Spacing.space4),
                )
                WeekTrends(state.trends, state.unit)
                if (state.trends.recovery.isNotEmpty()) RecoveryTrend(state.trends)
                if (state.records.isNotEmpty()) Records(state.records, state.trends.recordsInWeeks)
                if (state.trends.tests.isNotEmpty()) Tests(state.trends.tests)
            }
        }
    }
}

/** The cards of a bar a week: the time, the weight lifted in [unit] and the heart's load, each only with any. */
@Composable
private fun WeekTrends(trends: Trends, unit: WeightUnit) {
    if (trends.hasTime) {
        WeekTrend(stringResource(Res.string.trends_time_title), trends.weeks, { it.minutes.toDouble() }) {
            timeSpent((it * SECONDS_PER_MINUTE).toInt())
        }
    }
    if (trends.hasLifted) {
        WeekTrend(stringResource(Res.string.trends_lifted_title), trends.weeks, TrendWeek::lifted) {
            tonnageValue(it.roundToInt().toDouble(), unit)
        }
    }
    if (trends.hasHeartLoad) {
        WeekTrend(
            stringResource(Res.string.trends_heart_title),
            trends.weeks,
            { it.heartLoad.toDouble() },
            hint = stringResource(Res.string.trends_heart_hint),
        ) { it.roundToInt().toString() }
    }
}

/**
 * A bar a week of [value], this week outlined and the usual week dashed across, over this week's figure set against
 * it, each worded by [words].
 */
@Composable
private fun WeekTrend(
    title: String,
    weeks: List<TrendWeek>,
    value: (TrendWeek) -> Double,
    hint: String? = null,
    words: @Composable (Double) -> String,
) {
    val palette = IntervalTheme.palette
    val usual = weeks.usual(value)
    val now = weeks.lastOrNull()?.let(value) ?: 0.0
    val values = weeks.map { words(value(it)) }
    SummaryCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            Text(
                title,
                style = IntervalTheme.type.label,
                color = palette.text,
                modifier = Modifier.semantics { heading() },
            )
            hint?.let { Text(it, style = IntervalTheme.type.caption, color = palette.textDim) }
        }
        Bars(
            bars = weeks.map { Bar(value(it).toFloat(), filled = !it.current, outlined = it.current) },
            max = maxOf(weeks.maxOfOrNull(value) ?: 0.0, usual ?: 0.0).toFloat(),
            description = stringResource(Res.string.trends_bars_cd, title, values.joinToString()),
            line = usual?.toFloat(),
        )
        Text(
            if (usual == null) {
                stringResource(Res.string.trends_this_week, words(now))
            } else {
                stringResource(Res.string.trends_this_week_usual, words(now), words(usual))
            },
            style = IntervalTheme.type.meta,
            color = palette.textDim,
        )
    }
}

/** The heart's drop a minute into its rests, a workout a point, the first and latest, and which way it is going. */
@Composable
private fun RecoveryTrend(trends: Trends) {
    val palette = IntervalTheme.palette
    val drops = trends.recovery.map { it.drop }
    SummaryCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            Text(
                stringResource(Res.string.trends_recovery_title),
                style = IntervalTheme.type.label,
                color = palette.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(Res.string.trends_recovery_hint),
                style = IntervalTheme.type.caption,
                color = palette.textDim,
            )
        }
        Text(
            if (drops.size == 1) {
                stringResource(Res.string.trends_recovery_bpm, drops.single())
            } else {
                stringResource(Res.string.trends_recovery_range, drops.first(), drops.last())
            },
            style = IntervalTheme.type.title,
            color = palette.text,
        )
        if (drops.size > 1) {
            Sparkline(
                heights = drops.heights(),
                description = stringResource(
                    Res.string.trends_recovery_cd,
                    drops.size,
                    drops.joinToString(),
                ),
            )
        }
        trends.recoveryDirection?.let { direction ->
            Text(
                stringResource(
                    when (direction) {
                        RecoveryDirection.Faster -> Res.string.trends_recovery_faster
                        RecoveryDirection.Steady -> Res.string.trends_recovery_steady
                        RecoveryDirection.Slower -> Res.string.trends_recovery_slower
                    },
                ),
                style = IntervalTheme.type.meta,
                color = if (direction == RecoveryDirection.Slower) palette.textDim else palette.accent,
            )
        }
    }
}

/** The records set, newest first, a row each: the lift, the record as it stood after, and the day. */
@Composable
private fun Records(records: List<RecordLine>, inWeeks: Int) {
    val palette = IntervalTheme.palette
    Column {
        GroupLabel(stringResource(Res.string.trends_records_title))
        ListCard {
            if (inWeeks > 0) {
                Text(
                    pluralStringResource(Res.plurals.trends_records_count, inWeeks, inWeeks),
                    style = IntervalTheme.type.meta,
                    color = palette.textDim,
                    modifier = Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space12),
                )
                RowDivider(Modifier.padding(start = Spacing.space16))
            }
            records.forEachIndexed { index, line ->
                if (index > 0) RowDivider(Modifier.padding(start = Spacing.space16))
                RecordRow(line)
            }
        }
    }
}

@Composable
private fun RecordRow(line: RecordLine) {
    val palette = IntervalTheme.palette
    val record = line.moment.record
    Column(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
    ) {
        Text(liftName(record.exercise, record.double), style = IntervalTheme.type.label, color = palette.text)
        Text(recordLine(record), style = IntervalTheme.type.meta, color = palette.textDim)
        Text(line.date, style = IntervalTheme.type.caption, color = palette.textDim)
    }
}

/** Each test attempted: its latest result at its bell, the first there beside it, and the line of them. */
@Composable
private fun Tests(tests: List<TestTrend>) {
    val palette = IntervalTheme.palette
    Column {
        GroupLabel(stringResource(Res.string.trends_tests_title))
        ListCard {
            tests.forEachIndexed { index, trend ->
                if (index > 0) RowDivider(Modifier.padding(start = Spacing.space16))
                val test = strengthTest(trend.test)
                val name = testName(trend.test)
                val latest = testResultLine(test, trend.latest, trend.load)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space8),
                ) {
                    Column {
                        Text(name, style = IntervalTheme.type.label, color = palette.text)
                        Text(latest, style = IntervalTheme.type.meta, color = palette.textDim)
                        if (trend.results.size > 1) {
                            Text(
                                stringResource(
                                    Res.string.trends_test_change,
                                    testCount(test, trend.first),
                                    testCount(test, trend.latest),
                                ),
                                style = IntervalTheme.type.meta,
                                color = palette.accent,
                            )
                        }
                    }
                    if (trend.results.size > 1) {
                        Sparkline(
                            heights = trend.results.heights(),
                            description = stringResource(
                                Res.string.trends_test_cd,
                                name,
                                latest,
                                trend.results.joinToString(),
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** These values as a line's heights, the lowest at the foot and the highest at the top; level ones run mid-height. */
private fun List<Int>.heights(): List<Float> {
    val low = min()
    val span = max() - low
    return map { if (span == 0) LEVEL_HEIGHT else (it - low).toFloat() / span }
}

private const val LEVEL_HEIGHT = 0.5f
private const val SECONDS_PER_MINUTE = 60

@PreviewFormFactors
@Composable
private fun TrendsScreenPreview() {
    val weeks = PREVIEW_MINUTES.mapIndexed { i, minutes ->
        TrendWeek(
            weekStart = i * PREVIEW_DAYS_PER_WEEK.toLong(),
            minutes = minutes,
            lifted = minutes * PREVIEW_KG_A_MINUTE,
            heartLoad = minutes * PREVIEW_LOAD_A_MINUTE / PREVIEW_LOAD_SCALE,
            current = i == PREVIEW_MINUTES.lastIndex,
        )
    }
    val record = PersonalRecord.Weighted(
        BuiltInExercise.Snatch,
        heaviestKg = PREVIEW_BELL,
        repsAtHeaviest = PREVIEW_RECORD_REPS,
        mostReps = PREVIEW_RECORD_REPS,
        mostRepsKg = PREVIEW_BELL,
    )
    val trends = Trends(
        weeks = weeks,
        recovery = PREVIEW_DROPS.mapIndexed { i, drop -> RecoveryPoint(i.toLong(), drop) },
        records = listOf(RecordMoment(0, "s", record)),
        recordsInWeeks = 1,
        tests = listOf(TestTrend(TestId.SnatchTest, Load.Bell(PREVIEW_BELL), PREVIEW_TESTS)),
    )
    IntervalTheme {
        TrendsScreen(
            TrendsUiState(
                trends = trends,
                unit = WeightUnit.Kg,
                since = "Aug 31",
                records = listOf(RecordLine("Nov 19", trends.records.single())),
                loading = false,
            ),
            onBack = {},
        )
    }
}

private val PREVIEW_MINUTES = listOf(95, 120, 80, 140, 0, 110, 150, 135, 160, 90, 170, 60)
private val PREVIEW_DROPS = listOf(22, 24, 23, 26, 25, 28, 29, 31)
private val PREVIEW_TESTS = listOf(78, 86, 94, 100)
private const val PREVIEW_DAYS_PER_WEEK = 7
private const val PREVIEW_KG_A_MINUTE = 24.0
private const val PREVIEW_LOAD_A_MINUTE = 3
private const val PREVIEW_LOAD_SCALE = 2
private const val PREVIEW_BELL = 24.0
private const val PREVIEW_RECORD_REPS = 12
