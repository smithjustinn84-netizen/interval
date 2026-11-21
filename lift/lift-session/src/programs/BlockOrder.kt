package dev.justinsmith.lift.programs

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import dev.justinsmith.interval.ui.DragHandle
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.pressScale
import dev.justinsmith.interval.ui.rememberReorderState
import dev.justinsmith.interval.ui.reorderItem
import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.Block
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.editor_reorder_block_cd
import dev.justinsmith.lift.ui.blockLine
import dev.justinsmith.lift.ui.liftName
import org.jetbrains.compose.resources.stringResource

/**
 * A day's blocks, each a row that opens it; with more than one, each starts with a handle that drags it to another
 * place in the day ([onMove], a place at a time), and a screen reader's Move up and Move down on it.
 */
@Composable
internal fun DayBlockRows(
    blocks: List<Block>,
    bands: List<Band>,
    onOpen: (Int) -> Unit,
    onMove: (index: Int, by: Int) -> Unit,
) {
    val palette = IntervalTheme.palette
    val order = rememberReorderState()
    blocks.forEachIndexed { i, block ->
        Column(
            Modifier
                .reorderItem(order, i)
                .background(if (order.dragging == i) palette.surfaceVariant else palette.surface),
        ) {
            BlockRow(
                block,
                bands,
                onOpen = { onOpen(i) },
                handle = if (blocks.size > 1) {
                    {
                        val name = liftName(block.exercise, block.double)
                        DragHandle(
                            stringResource(Res.string.editor_reorder_block_cd, name),
                            i,
                            blocks.size,
                            order,
                            onMove,
                        )
                    }
                } else {
                    null
                },
            )
            RowDivider()
        }
    }
}

@Composable
private fun BlockRow(block: Block, bands: List<Band>, onOpen: () -> Unit, handle: (@Composable () -> Unit)?) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.listRow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (handle != null) Box(Modifier.padding(start = Spacing.space4)) { handle() }
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = Sizes.listRow)
                .clickable(interaction, LocalIndication.current, role = Role.Button, onClick = onOpen)
                .pressScale(interaction)
                .then(if (handle == null) Modifier.padding(start = Spacing.space16) else Modifier)
                .padding(end = Spacing.space16, top = Spacing.space12, bottom = Spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(liftName(block.exercise, block.double), style = IntervalTheme.type.label, color = palette.text)
                Text(blockLine(block, bands), style = IntervalTheme.type.meta, color = palette.textDim)
            }
            Icon(IntervalIcons.Chevron, null, tint = palette.textDim, modifier = Modifier.size(Sizes.icon))
        }
    }
}
