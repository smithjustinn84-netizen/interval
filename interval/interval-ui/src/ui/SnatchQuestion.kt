package dev.justinsmith.interval.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.SnatchCount
import dev.justinsmith.interval.ui.resources.Res
import dev.justinsmith.interval.ui.resources.snatch_bell
import dev.justinsmith.interval.ui.resources.snatch_fewer_cd
import dev.justinsmith.interval.ui.resources.snatch_more_cd
import dev.justinsmith.interval.ui.resources.snatch_not_counted
import dev.justinsmith.interval.ui.resources.snatch_per_set
import dev.justinsmith.interval.ui.resources.snatch_question
import dev.justinsmith.interval.ui.resources.snatch_step_hint
import dev.justinsmith.interval.ui.resources.snatch_uncounted_hint
import dev.justinsmith.lift.domain.formatKg
import dev.justinsmith.lift.domain.standardBells
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * What "About how many snatches a set?" shows of a workout of kettlebell intervals that switch hands: its [count]
 * once said, null until then; the count it [opensOn] meanwhile; and the [bells] it offers.
 */
data class SnatchAsk(val count: SnatchCount?, val opensOn: SnatchCount, val bells: List<Double>) {
    /** The count as the question shows it: the one said, or the one it opens on. */
    val shown: SnatchCount get() = count ?: opensOn

    companion object {
        /**
         * The question for a workout counted at [count], with [history] behind it and the bells [owned] in Lift: it
         * opens on the last count said and its bell, else about eight at the lightest bell owned, else at 16 kg, and
         * offers the [BELLS_SHOWN] bells owned (or Lift's standard sizes) nearest the one it shows.
         */
        fun of(count: SnatchCount?, history: List<HistoryEntry>, owned: List<Double>): SnatchAsk {
            val last = history.filter { it.snatches != null }.maxByOrNull { it.finishedAtEpochMillis }?.snatches
            val opensOn = last ?: SnatchCount(
                SnatchCount.USUAL_PER_SET,
                owned.minOrNull() ?: SnatchCount.USUAL_BELL_KG,
            )
            val bell = (count ?: opensOn).bellKg
            val sizes = (owned.ifEmpty { standardBells } + bell).distinct()
            val bells = sizes.sortedBy { abs(it - bell) }.take(BELLS_SHOWN).sorted()
            return SnatchAsk(count, opensOn, bells)
        }

        /** As many bells as fit a phone's width in one row. */
        const val BELLS_SHOWN = 5
    }
}

/**
 * "About how many snatches a set?" under a workout of kettlebell snatches: the count a set between − and +, and
 * the bell. A step or a bell picked counts the workout ([onCount]); "Not counted" takes it back (null). Until then
 * nothing is counted, and it says so.
 */
@Composable
fun SnatchQuestion(ask: SnatchAsk, onCount: (SnatchCount?) -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val shown = ask.shown
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(Res.string.snatch_question),
            style = IntervalTheme.type.label,
            color = palette.textDim,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        SheetStepper(
            shown.perSet,
            stringResource(Res.string.snatch_per_set, shown.perSet),
            stringResource(Res.string.snatch_fewer_cd),
            stringResource(Res.string.snatch_more_cd),
            canLower = shown.perSet > SnatchCount.MIN_PER_SET,
            canRaise = shown.perSet < SnatchCount.MAX_PER_SET,
            onStep = { onCount(shown.copy(perSet = shown.perSet + it)) },
            hint = stringResource(Res.string.snatch_step_hint),
            style = IntervalTheme.type.value,
        )
        SegmentedPicker(
            ask.bells,
            ask.count?.bellKg,
            label = { stringResource(Res.string.snatch_bell, formatKg(it)) },
            onSelect = { onCount(shown.copy(bellKg = it)) },
        )
        if (ask.count == null) {
            Text(
                stringResource(Res.string.snatch_uncounted_hint),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
        } else {
            QuietTextButton(stringResource(Res.string.snatch_not_counted), onClick = { onCount(null) })
        }
    }
}
