package dev.justinsmith.interval.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.platform.prefersReducedMotion
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.common_joined
import dev.justinsmith.interval.resources.licenses_fonts
import dev.justinsmith.interval.resources.licenses_hide
import dev.justinsmith.interval.resources.licenses_libraries
import dev.justinsmith.interval.resources.licenses_show
import dev.justinsmith.interval.resources.licenses_title
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.EnterEasing
import dev.justinsmith.interval.ui.ExitEasing
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.ScrollPage
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.settleSpring
import dev.justinsmith.interval.ui.surfaceButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** The licences screen has nothing to resolve; this keeps the route and screen split every screen has. */
@Composable
fun LicensesRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    LicensesScreen(onBack = onBack, modifier = modifier)
}

/**
 * Every font and library the app ships with, each with its licence and copyright holder. A row with
 * a licence text opens to show it in full, under the row.
 */
@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    ScrollPage(stringResource(Res.string.licenses_title), modifier = modifier, navigation = { BackAction(onBack) }) {
        GroupLabel(stringResource(Res.string.licenses_fonts))
        CreditList(fontCredits)
        GroupLabel(stringResource(Res.string.licenses_libraries))
        CreditList(libraryCredits)
    }
}

@Composable
private fun CreditList(credits: List<Credit>, modifier: Modifier = Modifier) {
    ListCard(modifier) {
        credits.forEachIndexed { index, credit ->
            if (index > 0) RowDivider()
            CreditRow(credit)
        }
    }
}

/**
 * A credit's name over its licence and holder. When the licence has a text, the row opens to show it,
 * its chevron turning to point the way, and the text unfolds beneath.
 */
@Composable
private fun CreditRow(credit: Credit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    var expanded by rememberSaveable { mutableStateOf(false) }
    val file = credit.license.file
    val licenseName = stringResource(credit.license.nameRes)
    val line = credit.holder?.let { stringResource(Res.string.common_joined, licenseName, it) } ?: licenseName
    val turn by animateFloatAsState(if (expanded) CHEVRON_OPEN_DEGREES else 0f, settleSpring(), label = "chevron")
    val reducedMotion = prefersReducedMotion()
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.listRow)
                .then(if (file != null) Modifier.creditToggle(expanded) { expanded = !expanded } else Modifier)
                .padding(start = Spacing.space16, end = Spacing.space12, top = Spacing.space8, bottom = Spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
        ) {
            Column(Modifier.weight(1f)) {
                Text(credit.name, style = IntervalTheme.type.label, color = palette.text)
                Text(line, style = IntervalTheme.type.meta, color = palette.textDim)
            }
            if (file != null) {
                Icon(
                    IntervalIcons.ChevronDown,
                    contentDescription = null,
                    tint = palette.textDim,
                    modifier = Modifier.size(Sizes.icon).graphicsLayer { rotationZ = turn },
                )
            }
        }
        if (file != null) {
            AnimatedVisibility(
                expanded,
                enter = if (reducedMotion) {
                    fadeIn(tween(Motion.CONTENT_MILLIS))
                } else {
                    expandVertically(tween(Motion.CONTENT_MILLIS, easing = EnterEasing)) +
                        fadeIn(tween(Motion.CONTENT_MILLIS))
                },
                exit = if (reducedMotion) {
                    fadeOut(tween(Motion.EXIT_MILLIS))
                } else {
                    shrinkVertically(tween(Motion.EXIT_MILLIS, easing = ExitEasing)) +
                        fadeOut(tween(Motion.EXIT_MILLIS))
                },
            ) {
                LicenseText(file)
            }
        }
    }
}

@Composable
private fun Modifier.creditToggle(expanded: Boolean, onToggle: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(if (expanded) Res.string.licenses_hide else Res.string.licenses_show)
    return surfaceButton(interaction, Corners.radius12, Color.Transparent, onToggle, onClickLabel = label)
        .semantics {
            if (expanded) {
                collapse {
                    onToggle()
                    true
                }
            } else {
                expand {
                    onToggle()
                    true
                }
            }
        }
}

/**
 * The licence as its project wrote it, read from the app's files the first time it is opened and reflowed.
 * Off the main thread: reading a resource only looks as if it suspends (Android reads the asset on the caller),
 * and the reflow runs regular expressions over the whole file.
 */
@Composable
private fun LicenseText(file: String, modifier: Modifier = Modifier) {
    val text by produceState<String?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) { reflow(Res.readBytes(file).decodeToString()) }
    }
    Text(
        text.orEmpty(),
        style = IntervalTheme.type.meta,
        color = IntervalTheme.palette.textDim,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.space16, end = Spacing.space16, bottom = Spacing.space16),
    )
}

/** An open row's downward chevron flips up. */
private const val CHEVRON_OPEN_DEGREES = 180f

@PreviewFormFactors
@Composable
private fun LicensesScreenPreview() {
    IntervalTheme {
        LicensesScreen(onBack = {})
    }
}
