package dev.justinsmith.interval.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.class_detail_close_cd
import dev.justinsmith.interval.history.resources.class_detail_delete_menu
import dev.justinsmith.interval.history.resources.class_detail_delete_text
import dev.justinsmith.interval.history.resources.class_detail_delete_title
import dev.justinsmith.interval.history.resources.class_detail_length
import dev.justinsmith.interval.history.resources.class_detail_no_rounds
import dev.justinsmith.interval.history.resources.class_detail_rounds
import dev.justinsmith.interval.history.resources.class_heart_average_peak
import dev.justinsmith.interval.history.resources.class_heart_load
import dev.justinsmith.interval.history.resources.class_round
import dev.justinsmith.interval.history.resources.class_round_drop
import dev.justinsmith.interval.history.resources.class_round_figures
import dev.justinsmith.interval.history.resources.common_cancel
import dev.justinsmith.interval.history.resources.common_joined
import dev.justinsmith.interval.history.resources.detail_delete_confirm
import dev.justinsmith.interval.history.resources.history_more_options_cd
import dev.justinsmith.interval.history.resources.notice_class_deleted
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.plan.sportTitle
import dev.justinsmith.interval.platform.heartRateZone
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.HeartRateChart
import dev.justinsmith.interval.ui.HeartReading
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalMenu
import dev.justinsmith.interval.ui.IntervalMenuItem
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.JoinedText
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.LocalRestingHeartRate
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.ScrollPage
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.ZoneBreakdown
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Resolves the page's ViewModel for the class that started at [startedAtEpochMillis] and binds it to
 * [ClassDetailScreen]; once it is gone, deleted here or cleared, [onBack]. [inPane] when it sits beside History's log.
 */
@Composable
fun ClassDetailRoute(
    startedAtEpochMillis: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    inPane: Boolean = false,
) {
    val viewModel = koinViewModel<ClassDetailViewModel>(key = "class-$startedAtEpochMillis") {
        parametersOf(startedAtEpochMillis)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gone = state.recorded == null
    val leave by rememberUpdatedState(onBack)
    LaunchedEffect(gone) { if (gone) leave() }
    val notices = LocalNotices.current
    val deleted = stringResource(Res.string.notice_class_deleted)
    ClassDetailScreen(
        state = state,
        onBack = onBack,
        // The notice outlives this page, which leaves once the class is gone.
        onDelete = {
            viewModel.delete()
            notices.show(deleted)
        },
        modifier = modifier,
        inPane = inPane,
    )
}

/**
 * One class recorded on a sensor: when it was, how long, its heart rate drawn over its length with the rounds
 * shaded, then a row a round saying its length, heart rate and how far that fell after. The overflow deletes the
 * recording, behind a confirmation; the day it was logged on stays.
 */
@Composable
fun ClassDetailScreen(
    state: ClassDetailUiState,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    inPane: Boolean = false,
) {
    val recorded = state.recorded ?: return
    var menu by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val title = sportTitle(recorded.sport)
    ScrollPage(
        title,
        modifier,
        navigation = { if (!inPane) BackAction(onBack) },
        actions = {
            if (inPane) IconAction(IntervalIcons.Close, stringResource(Res.string.class_detail_close_cd), onBack)
            IntervalMenu(
                expanded = menu,
                onExpandedChange = { menu = it },
                items = {
                    IntervalMenuItem(
                        stringResource(Res.string.class_detail_delete_menu),
                        onClick = { confirming = true },
                        destructive = true,
                    )
                },
            ) {
                IconAction(
                    IntervalIcons.More,
                    stringResource(Res.string.history_more_options_cd),
                    onClick = { menu = true },
                )
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
            ClassFigures(recorded)
            ClassRounds(recorded.rounds)
        }
    }
    if (confirming) {
        ConfirmDialog(
            title = stringResource(Res.string.class_detail_delete_title),
            text = stringResource(Res.string.class_detail_delete_text, title, dayHeading(recorded.day)),
            confirm = stringResource(Res.string.detail_delete_confirm),
            dismiss = stringResource(Res.string.common_cancel),
            onConfirm = {
                confirming = false
                onDelete()
            },
            onDismiss = { confirming = false },
            destructive = true,
        )
    }
}

/**
 * When it was and its length, then its heart rate as a finished workout's reads: the average with its zone, its dot
 * held still, the heart rate drawn with a band a round, and the time in each zone; under that card the peak and
 * what the class cost the heart.
 */
@Composable
private fun ColumnScope.ClassFigures(recorded: RecordedClass) {
    val palette = IntervalTheme.palette
    Text(
        stringResource(Res.string.common_joined, dayHeading(recorded.day), recorded.time),
        style = IntervalTheme.type.label,
        color = palette.textDim,
        modifier = Modifier.padding(horizontal = Spacing.space4),
    )
    SummaryCard {
        Text(
            stringResource(Res.string.class_detail_length),
            style = IntervalTheme.type.caption,
            color = palette.textDim,
        )
        Text(recorded.length, style = IntervalTheme.type.display, color = palette.text)
    }
    val average = recorded.averageBpm
    val peak = recorded.peakBpm
    SummaryCard {
        if (average != null) {
            HeartReading(
                average,
                heartRateZone(average, recorded.maxHeartRate, LocalRestingHeartRate.current),
                onWorkoutGround = false,
                beating = false,
            )
        }
        HeartRateChart(recorded.samples, maxBpm = recorded.maxHeartRate, marks = recorded.marks)
        ZoneBreakdown(recorded.samples, maxBpm = recorded.maxHeartRate)
    }
    val peakWords = peak?.let { stringResource(Res.string.class_heart_average_peak, it) }
    val loadWords = recorded.heartLoad?.let { stringResource(Res.string.class_heart_load, it) }
    val read = if (peakWords != null && loadWords != null) {
        stringResource(Res.string.common_joined, peakWords, loadWords)
    } else {
        peakWords ?: loadWords
    }
    if (read != null) {
        JoinedText(
            read,
            style = IntervalTheme.type.meta,
            color = palette.textDim,
            modifier = Modifier.padding(horizontal = Spacing.space4),
        )
    }
}

/** A row a round, or a line saying none was found. */
@Composable
private fun ClassRounds(rounds: List<RoundLine>) {
    val palette = IntervalTheme.palette
    Column {
        GroupLabel(stringResource(Res.string.class_detail_rounds))
        ListCard {
            if (rounds.isEmpty()) {
                Text(
                    stringResource(Res.string.class_detail_no_rounds),
                    style = IntervalTheme.type.body,
                    color = palette.textDim,
                    modifier = Modifier.padding(Spacing.space16),
                )
            }
            rounds.forEachIndexed { index, round ->
                if (index > 0) RowDivider(Modifier.padding(start = Spacing.space16))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
                ) {
                    Text(
                        stringResource(
                            Res.string.common_joined,
                            stringResource(Res.string.class_round, round.number),
                            round.length,
                        ),
                        style = IntervalTheme.type.label,
                        color = palette.text,
                        modifier = Modifier.semantics { heading() },
                    )
                    val figures = stringResource(Res.string.class_round_figures, round.averageBpm, round.peakBpm)
                    val drop = round.drop?.let { stringResource(Res.string.class_round_drop, it, round.dropSeconds) }
                    Text(
                        drop?.let { stringResource(Res.string.common_joined, figures, it) } ?: figures,
                        style = IntervalTheme.type.meta,
                        color = palette.textDim,
                    )
                }
            }
        }
    }
}

@PreviewFormFactors
@Composable
private fun ClassDetailScreenPreview() {
    val samples = List(PREVIEW_SAMPLES) { index ->
        val inRound = index in PREVIEW_ROUND_ONE || index in PREVIEW_ROUND_TWO
        (if (inRound) PREVIEW_HIGH else PREVIEW_LOW) + index % PREVIEW_WAVE * PREVIEW_RIPPLE
    }
    val recorded = RecordedClass(
        sport = Sport.Bjj,
        day = HistoryDay.On("Wednesday, Oct 14"),
        time = "6:32 PM",
        length = "1:02:10",
        averageBpm = 138,
        peakBpm = 181,
        heartLoad = PREVIEW_HEART_LOAD,
        samples = samples,
        marks = listOf(PREVIEW_ROUND_ONE, PREVIEW_ROUND_TWO).map {
            it.first.toFloat() / PREVIEW_SAMPLES..it.last.toFloat() / PREVIEW_SAMPLES
        },
        rounds = listOf(
            RoundLine(1, "5:05", averageBpm = 164, peakBpm = 178, drop = 24, dropSeconds = 60),
            RoundLine(2, "4:55", averageBpm = 169, peakBpm = 181, drop = 11, dropSeconds = 30),
        ),
    )
    IntervalTheme { ClassDetailScreen(ClassDetailUiState(recorded), onBack = {}, onDelete = {}) }
}

private const val PREVIEW_SAMPLES = 120
private val PREVIEW_ROUND_ONE = 40..65
private val PREVIEW_ROUND_TWO = 75..100
private const val PREVIEW_WAVE = 5
private const val PREVIEW_RIPPLE = 3
private const val PREVIEW_HIGH = 162
private const val PREVIEW_LOW = 108
private const val PREVIEW_HEART_LOAD = 96
