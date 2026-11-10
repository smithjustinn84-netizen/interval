package dev.justinsmith.interval.welcome

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.app_name
import dev.justinsmith.interval.resources.welcome_about
import dev.justinsmith.interval.resources.welcome_ahead
import dev.justinsmith.interval.resources.welcome_next
import dev.justinsmith.interval.resources.welcome_page_cd
import dev.justinsmith.interval.resources.welcome_set_up
import dev.justinsmith.interval.resources.welcome_skip
import dev.justinsmith.interval.resources.welcome_skip_to_set_up
import dev.justinsmith.interval.resources.welcome_start
import dev.justinsmith.interval.resources.welcome_tagline
import dev.justinsmith.interval.splash.drawIntervalMark
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.AppSizes
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ModalBackHandler
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.SetupLanes
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import org.jetbrains.compose.resources.stringResource

/** The pages after the opening one: Today, Train and Log, a tab each. */
internal const val WELCOME_TABS = 3

/**
 * The welcome a new install opens on, over Today: what the app is, then a page for each tab, then Set up, which
 * [onSetUp] puts in its place (from the last page, and from Skip on any). The page shown is kept across a
 * rotation; the system's Back steps a page back, and from the opening page leaves for Today as any page does.
 */
@Composable
fun WelcomeRoute(onSetUp: () -> Unit, modifier: Modifier = Modifier) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val setUp by rememberUpdatedState(onSetUp)
    if (page > 0) ModalBackHandler { page-- }
    val actions = remember {
        WelcomeActions(
            onNext = { if (page < WELCOME_TABS) page++ else setUp() },
            onBack = { if (page > 0) page-- },
            onSkip = { setUp() },
        )
    }
    WelcomeScreen(page, actions, modifier)
}

/** What the welcome's controls do. Each defaults to nothing, so a preview or test sets only its own. */
@Immutable
class WelcomeActions(
    /** Get started and Next; on the last page, Set up my week. */
    val onNext: () -> Unit = {},
    /** The page before, from a tab's page. */
    val onBack: () -> Unit = {},
    /** Straight to Set up. */
    val onSkip: () -> Unit = {},
)

/**
 * The welcome's [page]: 0 the opening (the mark, the name, what the app is, and how much is ahead), 1 to
 * [WELCOME_TABS] a tab's page, headed by the tab as the bar shows it selected and showing what is on it. Over the
 * lanes, in the column a pushed page keeps, its one action pinned at the foot.
 */
@Composable
fun WelcomeScreen(page: Int, actions: WelcomeActions, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(IntervalTheme.palette.background), contentAlignment = Alignment.TopCenter) {
        SetupLanes(Modifier.matchParentSize())
        Column(
            Modifier
                .widthIn(max = Sizes.content)
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = Spacing.space16)
                .padding(bottom = Spacing.space16),
        ) {
            if (page > 0) PageBar(page, actions)
            AnimatedContent(page, Modifier.weight(1f).fillMaxWidth(), label = "welcome") { shown ->
                if (shown == 0) Opening() else TabPage(shown)
            }
            Foot(page, actions)
        }
    }
}

/** A tab's page's bar: Back, how far through the three pages, and Skip. */
@Composable
private fun PageBar(page: Int, actions: WelcomeActions, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val described = stringResource(Res.string.welcome_page_cd, page, WELCOME_TABS)
    Box(modifier.fillMaxWidth().padding(top = Spacing.space8), contentAlignment = Alignment.Center) {
        BackAction(actions.onBack, Modifier.align(Alignment.CenterStart))
        // The workout's round marks: the pages gone by, the one shown, the ones to come.
        Row(
            Modifier.clearAndSetSemantics { contentDescription = described },
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            for (mark in 1..WELCOME_TABS) {
                val fill = when {
                    mark < page -> palette.text
                    mark == page -> palette.accent
                    else -> palette.surfaceVariant
                }
                Box(
                    Modifier
                        .size(IntervalSizes.roundMarkWidth, IntervalSizes.roundMarkHeight)
                        .clip(Corners.full)
                        .background(fill),
                )
            }
        }
        QuietTextButton(
            stringResource(Res.string.welcome_skip),
            actions.onSkip,
            Modifier.align(Alignment.CenterEnd),
        )
    }
}

/** The opening page: the mark at rest, the name as the intro sets it, and what the app is in two lines. */
@Composable
private fun Opening(modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.space8, vertical = Spacing.space24),
        verticalArrangement = Arrangement.spacedBy(Spacing.space16, Alignment.Bottom),
    ) {
        Canvas(Modifier.size(AppSizes.welcomeMark)) {
            drawIntervalMark(center, unit = size.minDimension / MARK_VISIBLE_UNITS)
        }
        Text(
            stringResource(Res.string.app_name),
            style = IntervalTheme.type.display,
            color = palette.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(Res.string.welcome_tagline), style = IntervalTheme.type.value, color = palette.text)
        Text(stringResource(Res.string.welcome_about), style = IntervalTheme.type.body, color = palette.textDim)
    }
}

/** The page's one action, and on the opening page how much is ahead and the way past it. */
@Composable
private fun ColumnScope.Foot(page: Int, actions: WelcomeActions) {
    if (page == 0) {
        Text(
            stringResource(Res.string.welcome_ahead),
            style = IntervalTheme.type.meta,
            color = IntervalTheme.palette.textDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.space8),
        )
    }
    val next = when (page) {
        0 -> Res.string.welcome_start
        WELCOME_TABS -> Res.string.welcome_set_up
        else -> Res.string.welcome_next
    }
    AccentButton(stringResource(next), actions.onNext, Modifier.fillMaxWidth())
    if (page == 0) {
        QuietTextButton(
            stringResource(Res.string.welcome_skip_to_set_up),
            actions.onSkip,
            Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.space8),
        )
    }
}

/** The mark's ring and a little air, in the units `drawIntervalMark` measures in: no coin round it here. */
private const val MARK_VISIBLE_UNITS = 64f
