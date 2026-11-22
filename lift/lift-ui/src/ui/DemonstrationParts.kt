package dev.justinsmith.lift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.domain.Fault
import dev.justinsmith.lift.ui.resources.Res
import dev.justinsmith.lift.ui.resources.demo_done_wrong
import dev.justinsmith.lift.ui.resources.demo_hide_wrong
import dev.justinsmith.lift.ui.resources.demo_show_wrong
import dev.justinsmith.lift.ui.resources.demo_watch_for
import org.jetbrains.compose.resources.stringResource

/**
 * Under Watch for, the [faults] a lift is prone to, each by the words that put it right: one the card's drawing can
 * show done wrong ([showable], by its place) is tapped to show it ([onToggle]) and lit while it shows ([shown]); one
 * it can't is words alone. Nothing for a lift with none.
 */
@Composable
internal fun FaultRows(faults: List<Fault>, showable: List<Boolean>, shown: Int, onToggle: (Int) -> Unit) {
    if (faults.isEmpty()) return
    CueGroupLabel(stringResource(Res.string.demo_watch_for))
    faults.forEachIndexed { i, fault ->
        if (i > 0) RowDivider()
        CueRow(
            stringResource(fault.words()),
            current = i == shown,
            lit = IntervalTheme.palette.danger,
            onShow = if (showable[i]) {
                { onToggle(i) }
            } else {
                null
            },
            showLabel = stringResource(if (i == shown) Res.string.demo_hide_wrong else Res.string.demo_show_wrong),
        )
    }
}

/**
 * Over a figure doing its lift wrong, so it is never taken for the way to do it. A screen reader hears it in the
 * figure's own description, so the mark itself says nothing.
 */
@Composable
internal fun DoneWrongMark(modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Text(
        stringResource(Res.string.demo_done_wrong),
        style = IntervalTheme.type.meta,
        color = palette.onDanger,
        modifier = modifier
            .clearAndSetSemantics { }
            .background(palette.danger, Corners.full)
            .padding(horizontal = Spacing.space8, vertical = Spacing.space4),
    )
}
