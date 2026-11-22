package dev.justinsmith.lift.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.heartRateZone
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.HeartRateChart
import dev.justinsmith.interval.ui.HeartReading
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.LocalRestingHeartRate
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.ZoneBreakdown
import dev.justinsmith.interval.ui.countUp
import dev.justinsmith.interval.ui.isLargeText
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.PersonalRecord
import dev.justinsmith.lift.domain.formatKg
import dev.justinsmith.lift.ui.RecordRow
import dev.justinsmith.lift.ui.StatTile
import dev.justinsmith.lift.ui.resources.Res
import dev.justinsmith.lift.ui.resources.kg_value
import dev.justinsmith.lift.ui.resources.session_heart_average_peak
import dev.justinsmith.lift.ui.resources.session_moved
import dev.justinsmith.lift.ui.resources.session_time
import dev.justinsmith.lift.ui.resources.summary_records
import dev.justinsmith.lift.ui.resources.summary_reps
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * A finished session's numbers: its time and reps, the weight moved, its heart rate once read from the health
 * store, then the [records] it set, if any, each tagged when [highlight]. The summary shows them as a session
 * ends, counting the three up from nothing as they arrive ([countUp]); its page in History shows them at once.
 * The heart rate's zones are worked out from [maxBpm].
 */
@Composable
fun SessionStats(
    session: LiftSession,
    records: List<PersonalRecord>,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    countUp: Boolean = false,
    maxBpm: Int = DEFAULT_MAX_BPM,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        TilePair(
            {
                StatTile(
                    stringResource(Res.string.session_time),
                    formatClock(countUp(session.elapsedSeconds(0), countUp)),
                    it,
                )
            },
            { StatTile(stringResource(Res.string.summary_reps), countUp(session.reps, countUp).toString(), it) },
        )
        // The kilograms count up whole, and read to the half once they have landed.
        val wholeKg = session.tonnageKg.roundToInt()
        val countedKg = countUp(wholeKg, countUp)
        StatTile(
            stringResource(Res.string.session_moved),
            stringResource(
                Res.string.kg_value,
                formatKg(if (countedKg == wholeKg) session.tonnageKg else countedKg.toDouble()),
            ),
            Modifier.fillMaxWidth(),
        )
        val average = session.averageBpm
        val peak = session.peakBpm
        if (average != null && peak != null) {
            // As the live heart card reads: the average with its zone, its dot held still since the session is
            // over, then the session's heart rate and its time in each zone, and the peak under the card.
            ListCard {
                Column(
                    Modifier.padding(Spacing.space16),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space12),
                ) {
                    HeartReading(
                        average,
                        heartRateZone(average, maxBpm, LocalRestingHeartRate.current),
                        onWorkoutGround = false,
                        beating = false,
                    )
                    val samples = session.heartRateSamples
                    if (!samples.isNullOrEmpty()) {
                        HeartRateChart(samples, maxBpm = maxBpm)
                        ZoneBreakdown(samples, maxBpm = maxBpm)
                    }
                }
            }
            Text(
                stringResource(Res.string.session_heart_average_peak, peak),
                Modifier.padding(horizontal = Spacing.space4),
                style = IntervalTheme.type.meta,
                color = IntervalTheme.palette.textDim,
            )
        }
        if (records.isNotEmpty()) {
            GroupLabel(stringResource(Res.string.summary_records))
            ListCard {
                records.forEachIndexed { i, record ->
                    if (i > 0) RowDivider()
                    RecordRow(record, tagged = highlight)
                }
            }
        }
    }
}

/**
 * Two tiles side by side, or with large text one over the other, each the whole width: half a phone's width breaks
 * a clock in two ("25:0 / 0").
 */
@Composable
private fun TilePair(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        maxItemsInEachRow = if (isLargeText()) 1 else 2,
    ) {
        first(Modifier.weight(1f))
        second(Modifier.weight(1f))
    }
}
