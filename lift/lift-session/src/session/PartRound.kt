package dev.justinsmith.lift.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.log_fewer_reps
import dev.justinsmith.lift.session.resources.log_more_reps
import dev.justinsmith.lift.session.resources.session_test_part_add
import dev.justinsmith.lift.session.resources.session_test_part_round
import dev.justinsmith.lift.ui.Stepper
import org.jetbrains.compose.resources.stringResource

/**
 * After a test's clock: the reps of the round it cut short, stepped up from none to one short of a [round], and
 * added to the count. A test against the clock counts every rep, and the clock logs whole rounds.
 */
@Composable
internal fun PartRound(round: Int, onAdd: (Int) -> Unit, modifier: Modifier = Modifier) {
    var reps by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
        Text(
            stringResource(Res.string.session_test_part_round),
            style = IntervalTheme.type.meta,
            color = IntervalTheme.palette.textDim,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            Stepper(
                reps.toString(),
                fewer = stringResource(Res.string.log_fewer_reps),
                more = stringResource(Res.string.log_more_reps),
                canLower = reps > 0,
                canRaise = reps < round - 1,
                onStep = { reps = (reps + it).coerceIn(0, round - 1) },
                modifier = Modifier.weight(1f),
                compact = true,
            )
            SecondaryButton(
                stringResource(Res.string.session_test_part_add),
                { onAdd(reps) },
                Modifier.weight(1f),
                enabled = reps > 0,
            )
        }
    }
}
