package dev.justinsmith.lift.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.domain.ReadinessReason
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.session_easier_grip
import dev.justinsmith.lift.session.resources.session_easier_hard
import dev.justinsmith.lift.session.resources.session_easier_lighter_day
import dev.justinsmith.lift.session.resources.session_easier_mat
import dev.justinsmith.lift.session.resources.session_easier_title
import dev.justinsmith.lift.session.resources.session_easier_use
import dev.justinsmith.lift.session.resources.session_easier_what
import org.jetbrains.compose.resources.stringResource

/**
 * On a taxed day, before the first set: why today could be easier, what easier is, Use it, and the program's day
 * lighter on the grip when there is one.
 */
@Composable
internal fun EasierCard(offer: EasierOffer, onUse: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    ListCard(modifier) {
        Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            Text(
                stringResource(Res.string.session_easier_title),
                style = IntervalTheme.type.label,
                color = palette.text,
            )
            val why = when (offer.reason) {
                ReadinessReason.HardSession -> Res.string.session_easier_hard
                ReadinessReason.Mat -> Res.string.session_easier_mat
                ReadinessReason.Grip -> Res.string.session_easier_grip
            }
            Text(stringResource(why), style = IntervalTheme.type.meta, color = palette.textDim)
            Text(
                stringResource(Res.string.session_easier_what),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
            offer.lighterDay?.let { day ->
                Text(
                    stringResource(Res.string.session_easier_lighter_day, day),
                    style = IntervalTheme.type.meta,
                    color = palette.textDim,
                )
            }
            QuietTextButton(stringResource(Res.string.session_easier_use), onUse)
        }
    }
}
