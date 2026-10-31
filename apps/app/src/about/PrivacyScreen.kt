package dev.justinsmith.interval.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.privacy_health
import dev.justinsmith.interval.resources.privacy_health_title
import dev.justinsmith.interval.resources.privacy_sharing
import dev.justinsmith.interval.resources.privacy_sharing_title
import dev.justinsmith.interval.resources.privacy_stored
import dev.justinsmith.interval.resources.privacy_stored_title
import dev.justinsmith.interval.resources.privacy_summary
import dev.justinsmith.interval.resources.privacy_title
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.ScrollPage
import dev.justinsmith.interval.ui.Spacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The privacy policy: what the app keeps, where, and what Health Connect gets when it is connected. The
 * same words Play's listing links to, and what Health Connect opens when asked why the app wants its
 * permissions (Android's `PrivacyActivity`), so it stands on its own without the app's container.
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    ScrollPage(stringResource(Res.string.privacy_title), modifier = modifier, navigation = { BackAction(onBack) }) {
        Text(
            stringResource(Res.string.privacy_summary),
            style = IntervalTheme.type.body,
            color = IntervalTheme.palette.text,
            modifier = Modifier.padding(top = Spacing.space16),
        )
        Section(Res.string.privacy_stored_title, Res.string.privacy_stored)
        Section(Res.string.privacy_sharing_title, Res.string.privacy_sharing)
        Section(Res.string.privacy_health_title, Res.string.privacy_health)
    }
}

@Composable
private fun Section(title: StringResource, body: StringResource, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(modifier.padding(top = Spacing.space24)) {
        Text(
            stringResource(title),
            style = IntervalTheme.type.label,
            color = palette.textDim,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(body),
            style = IntervalTheme.type.body,
            color = palette.text,
            modifier = Modifier.padding(top = Spacing.space8),
        )
    }
}

@PreviewFormFactors
@Composable
private fun PrivacyScreenPreview() {
    IntervalTheme {
        PrivacyScreen(onBack = {})
    }
}
