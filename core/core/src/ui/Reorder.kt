package dev.justinsmith.interval.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.reorder_move_down
import dev.justinsmith.core.resources.reorder_move_up
import org.jetbrains.compose.resources.stringResource

/**
 * A list put in another order by its items' handles ([DragHandle]): the item being dragged, the place it is at now,
 * how far the finger has taken it from there, and each item's height, which says when it has passed a neighbour.
 * The items lie [gap] pixels apart. The list itself is the caller's: each move is handed on a place at a time.
 */
@Stable
class ReorderState internal constructor(private val gap: Float) {
    /** The place of the item being dragged, null while none is. */
    var dragging by mutableStateOf<Int?>(null)
        private set
    internal var offset by mutableFloatStateOf(0f)
        private set
    private val heights = mutableStateMapOf<Int, Int>()

    internal fun measured(index: Int, height: Int) {
        heights[index] = height
    }

    internal fun start(at: Int) {
        dragging = at
        offset = 0f
    }

    internal fun end() {
        dragging = null
        offset = 0f
    }

    /**
     * [delta] further, of [count] items: past half its neighbour's height the item takes the neighbour's place,
     * through [onMove] (its place and 1 later or -1 earlier), and is then measured from there.
     */
    internal fun drag(delta: Float, count: Int, onMove: (Int, Int) -> Unit) {
        val at = dragging ?: return
        offset += delta
        val by = if (offset > 0) 1 else -1
        val neighbour = at + by
        if (neighbour !in 0 until count) return
        val step = (heights[neighbour] ?: return) + gap
        if (offset * by > step / 2f) {
            onMove(at, by)
            dragging = neighbour
            offset -= by * step
        }
    }
}

/** A [ReorderState] for items laid [gap] apart (none for rows that touch). */
@Composable
fun rememberReorderState(gap: Dp? = null): ReorderState {
    val px = with(LocalDensity.current) { gap?.toPx() ?: 0f }
    return remember(px) { ReorderState(px) }
}

/** Item [index] of a list [state] reorders: measured, and while dragged drawn over the rest where the finger is. */
fun Modifier.reorderItem(state: ReorderState, index: Int): Modifier = this
    .onSizeChanged { state.measured(index, it.height) }
    .zIndex(if (state.dragging == index) 1f else 0f)
    .graphicsLayer { translationY = if (state.dragging == index) state.offset else 0f }

/**
 * The handle of item [index] of [count] in a list [state] reorders, read as [description]: dragged up or down, it
 * takes the item with it a place at a time ([onMove], its place and 1 later or -1 earlier), ticking at each. A
 * screen reader has Move up and Move down on it instead.
 */
@Composable
fun DragHandle(
    description: String,
    index: Int,
    count: Int,
    state: ReorderState,
    onMove: (index: Int, by: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val up = stringResource(Res.string.reorder_move_up)
    val down = stringResource(Res.string.reorder_move_down)
    val currentIndex by rememberUpdatedState(index)
    val currentCount by rememberUpdatedState(count)
    val move by rememberUpdatedState(onMove)
    Box(
        modifier
            .size(Sizes.iconButton)
            .pointerInput(state) {
                detectVerticalDragGestures(
                    onDragStart = {
                        state.start(currentIndex)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = state::end,
                    onDragCancel = state::end,
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        state.drag(amount, currentCount) { from, by ->
                            move(from, by)
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }
                    },
                )
            }
            .semantics {
                contentDescription = description
                customActions = listOfNotNull(
                    CustomAccessibilityAction(up) { true.also { move(currentIndex, -1) } }.takeIf { index > 0 },
                    CustomAccessibilityAction(down) { true.also { move(currentIndex, 1) } }
                        .takeIf { index < count - 1 },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(IntervalIcons.DragHandle, null, tint = IntervalTheme.palette.textDim, modifier = Modifier.size(Sizes.icon))
    }
}
