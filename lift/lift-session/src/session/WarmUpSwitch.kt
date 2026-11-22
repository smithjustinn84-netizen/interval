package dev.justinsmith.lift.session

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.ToggleRow
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.log_warm_up
import dev.justinsmith.lift.session.resources.log_warm_up_note
import org.jetbrains.compose.resources.stringResource

/**
 * Whether the set is a warm-up: kept with the session's sets and said as one, but counted toward no record,
 * progress or total. The Log sheet's switches off again once its set is logged; the change sheets' says how the
 * set was logged.
 */
@Composable
internal fun WarmUpSwitch(warmUp: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    ListCard(modifier.padding(top = Spacing.space8)) {
        ToggleRow(
            stringResource(Res.string.log_warm_up),
            stringResource(Res.string.log_warm_up_note),
            checked = warmUp,
            onToggle = onToggle,
        )
    }
}
