package dev.justinsmith.lift.today

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.domain.MuscleBalance
import dev.justinsmith.lift.today.resources.Res
import dev.justinsmith.lift.today.resources.today_balance_span
import dev.justinsmith.lift.today.resources.today_balance_untrained
import dev.justinsmith.lift.ui.BalanceBars

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
