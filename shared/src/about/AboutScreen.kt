package dev.justinsmith.interval.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.interval.platform.AppInfo
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.about_licenses
import dev.justinsmith.interval.resources.about_licenses_hint
import dev.justinsmith.interval.resources.about_made_by
import dev.justinsmith.interval.resources.about_tagline
import dev.justinsmith.interval.resources.about_title
import dev.justinsmith.interval.resources.about_version
import dev.justinsmith.interval.resources.about_version_unknown
import dev.justinsmith.interval.resources.app_name
import dev.justinsmith.interval.splash.Mark
import dev.justinsmith.interval.splash.drawIntervalMark
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LinkRow
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.PhaseColors
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.ScrollPage
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.rememberEntrance
import dev.justinsmith.interval.ui.risingEnter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/** Reads the build's version and binds it to [AboutScreen]. */
@Composable
fun AboutRoute(onBack: () -> Unit, onShowLicenses: () -> Unit, modifier: Modifier = Modifier) {
    val info = currentKoinScope().get<AppInfo>()
    AboutScreen(version = info.version, onBack = onBack, onShowLicenses = onShowLicenses, modifier = modifier)
}

/**
 * The app's mark, name and [version] over a short line on what it is, the way to its licences, and
 * who made it. [version] is null for a build that carries none, which says so instead.
 */
@Composable
fun AboutScreen(version: String?, onBack: () -> Unit, onShowLicenses: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    ScrollPage(
        stringResource(Res.string.about_title),
        modifier = modifier,
        navigation = { BackAction(onBack) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The hero rises in once, as the summary's does; the card below is in place already.
        AnimatedVisibility(rememberEntrance(), enter = risingEnter()) { Hero(version) }
        ListCard(Modifier.padding(top = Spacing.space32)) {
            LinkRow(
                stringResource(Res.string.about_licenses),
                stringResource(Res.string.about_licenses_hint),
                onClick = onShowLicenses,
            )
        }
        Text(
            stringResource(Res.string.about_made_by),
            style = IntervalTheme.type.meta,
            color = palette.textDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.space24),
        )
    }
}

/** The launcher icon's coin with the mark at rest on it, the name beneath as the intro sets it, then the version. */
@Composable
private fun Hero(version: String?, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(modifier.padding(top = Spacing.space32), horizontalAlignment = Alignment.CenterHorizontally) {
        // Always the icon's ground, in both themes: in the dark one it melts into the page and the
        // mark floats, as it does on the intro; in the light one it is the coin the launcher shows.
        Canvas(Modifier.size(Sizes.aboutMark)) {
            drawCircle(PhaseColors.ground)
            drawIntervalMark(center, unit = size.minDimension / ICON_VISIBLE_UNITS)
        }
        Text(
            stringResource(Res.string.app_name),
            style = IntervalTheme.type.title,
            color = palette.text,
            modifier = Modifier.padding(top = Spacing.space16),
        )
        Text(
            version?.let { stringResource(Res.string.about_version, it) }
                ?: stringResource(Res.string.about_version_unknown),
            style = IntervalTheme.type.meta,
            color = palette.textDim,
        )
        Text(
            stringResource(Res.string.about_tagline),
            style = IntervalTheme.type.body,
            color = palette.textDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.space12),
        )
    }
}

/**
 * Of the mark's [Mark.CANVAS] units, a round launcher icon shows the middle 72 across. The coin here
 * is that circle, so the mark sits on it at the launcher's proportions.
 */
private const val ICON_VISIBLE_UNITS = 72f

@PreviewFormFactors
@Composable
private fun AboutScreenPreview() {
    IntervalTheme {
        AboutScreen(version = "0.1.0", onBack = {}, onShowLicenses = {})
    }
}
