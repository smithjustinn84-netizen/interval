package dev.justinsmith.interval.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.justinsmith.interval.history.UpNextItem
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.isFlowShaped
import dev.justinsmith.interval.ui.MiniTimeline

/**
 * The work and rest a planned interval workout runs through, so one tells Tabata from 15:15 at a glance; null for
 * anything else, a flow among them, whose moves say more than its shape.
 */
internal val UpNextItem.shape: IntervalConfig?
    get() = (this as? UpNextItem.Planned)?.config?.takeUnless { it.isFlowShaped }

/** [item]'s [shape] drawn small under its words with its work in [work]; nothing when it has none. */
@Composable
internal fun StepShape(item: UpNextItem, work: Color, modifier: Modifier = Modifier) {
    item.shape?.let { MiniTimeline(it, modifier, work = work) }
}
