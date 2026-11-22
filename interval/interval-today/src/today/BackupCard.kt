package dev.justinsmith.interval.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.today.resources.Res
import dev.justinsmith.interval.today.resources.backup_body
import dev.justinsmith.interval.today.resources.backup_export
import dev.justinsmith.interval.today.resources.backup_later
import dev.justinsmith.interval.today.resources.backup_title
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * The ask to back up, a month after the last backup with something new since: why, then Export, which offers the
 * whole log as a file to keep and so takes the card away, and Not now, which asks again in a month. Quiet: the
 * tab's one raised button stays the day's Start.
 */
@Composable
internal fun BackupCard(onBackUp: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    ListCard(modifier) {
        Column(
            Modifier.padding(start = Spacing.space16, end = Spacing.space16, top = Spacing.space16),
            verticalArrangement = Arrangement.spacedBy(Spacing.space4),
        ) {
            Text(
                stringResource(Res.string.backup_title),
                style = IntervalTheme.type.label,
                color = IntervalTheme.palette.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(Res.string.backup_body),
                style = IntervalTheme.type.meta,
                color = IntervalTheme.palette.textDim,
            )
        }
        Row(
            Modifier.padding(Spacing.space16),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecondaryButton(stringResource(Res.string.backup_export), onBackUp, Modifier.weight(1f))
            QuietTextButton(stringResource(Res.string.backup_later), onLater)
        }
    }
}

@PreviewFormFactors
@Composable
private fun BackupCardPreview() {
    IntervalTheme {
        BackupCard({}, {}, Modifier.padding(Spacing.space16))
    }
}
