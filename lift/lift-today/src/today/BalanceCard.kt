package dev.justinsmith.lift.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.domain.GripLoad
import dev.justinsmith.lift.domain.MuscleBalance
import dev.justinsmith.lift.today.resources.Res
import dev.justinsmith.lift.today.resources.today_balance_span
import dev.justinsmith.lift.today.resources.today_balance_untrained
import dev.justinsmith.lift.today.resources.today_grip_overreached
import dev.justinsmith.lift.today.resources.today_grip_thrown
import dev.justinsmith.lift.today.resources.today_grip_week
import dev.justinsmith.lift.ui.BalanceBars
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The grip's week: its load, how much of it thrown (swings, cleans, snatches) rather than slow, and, past what the
 * weeks before carried, the warning to spare it.
 */
@Composable
internal fun GripCard(grip: GripLoad, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    ListCard(modifier) {
        Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            Text(
                stringResource(Res.string.today_grip_week, grip.acute.roundToInt()),
                style = IntervalTheme.type.label,
                color = palette.text,
            )
            grip.ballisticShare?.let { share ->
                Text(
                    stringResource(Res.string.today_grip_thrown, (share * PERCENT).roundToInt()),
                    style = IntervalTheme.type.meta,
                    color = palette.textDim,
                )
            }
            if (grip.overreached) {
                Text(
                    stringResource(Res.string.today_grip_overreached),
                    style = IntervalTheme.type.meta,
                    color = palette.accent,
                )
            }
        }
    }
}

private const val PERCENT = 100

/** Sets per muscle group over the last four weeks, in a card, naming any group nothing trained. */
@Composable
internal fun BalanceCard(balance: MuscleBalance, modifier: Modifier = Modifier) {
    ListCard(modifier) {
        BalanceBars(
            balance,
            span = Res.string.today_balance_span,
            untrained = Res.string.today_balance_untrained,
            modifier = Modifier.padding(Spacing.space16),
        )
    }
}
