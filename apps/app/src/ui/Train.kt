package dev.justinsmith.interval.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.nav.ConditioningTab
import dev.justinsmith.interval.nav.MobilityTab
import dev.justinsmith.interval.nav.Tab
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.train_conditioning
import dev.justinsmith.interval.resources.train_mobility
import dev.justinsmith.interval.resources.train_strength
import dev.justinsmith.lift.nav.LiftTab
import org.jetbrains.compose.resources.stringResource

// Train is one destination of the bar in three parts, its pillars: each a tab of its own on the back stack, under
// the same title and the picker between them, so that Lift's side stays Lift's and the presets and the flows the
// interval side's.

/** The group Train's pillars share ([tabGroup]): moving between them swaps the page's content in place. */
internal const val TRAIN_GROUP = "train"

/** Train's pillars in the picker's order: Strength, Lift's tab, then Conditioning, the presets, then Mobility. */
internal val TrainPillars: List<Tab> = listOf(LiftTab, ConditioningTab, MobilityTab)

/** The picker of Train's pillars, across the top of each one's page under its title. [current] is the page's own. */
@Composable
internal fun PillarPicker(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    SegmentedPicker(
        options = TrainPillars,
        selected = current,
        label = {
            stringResource(
                when (it) {
                    LiftTab -> Res.string.train_strength
                    ConditioningTab -> Res.string.train_conditioning
                    else -> Res.string.train_mobility
                },
            )
        },
        // Three across a phone: a check beside the one chosen would cut "Conditioning" short.
        check = false,
        onSelect = { if (it != current) onSelect(it) },
        modifier = modifier.padding(horizontal = Spacing.space16).padding(bottom = Spacing.space8),
    )
}
