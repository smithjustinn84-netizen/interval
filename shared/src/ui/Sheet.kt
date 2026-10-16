package dev.justinsmith.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.composeunstyled.DragIndication
import com.composeunstyled.Scrim
import com.composeunstyled.Sheet
import com.composeunstyled.SheetDetent
import com.composeunstyled.UnstyledModalBottomSheet
import com.composeunstyled.rememberModalBottomSheetState
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.value_sheet_drag_handle_cd
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * A modal bottom sheet drawn from Interval tokens: the surface, rounded on top, a drag handle and
 * [content] under it, spaced as a form. The content gets [hide] for its own way out (Done, Cancel,
 * Save). Every way out, a swipe, back, Esc and a tap on the scrim included, ends with the sheet
 * slid away, and only then does the caller hear [onDismiss]. Screen readers announce it by [title].
 */
@Composable
fun IntervalSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(hide: () -> Unit) -> Unit,
) {
    val palette = IntervalTheme.palette
    val dragHandle = stringResource(Res.string.value_sheet_drag_handle_cd)
    // No half-open stop: every sheet is short enough to show whole.
    val sheetState = rememberModalBottomSheetState(initialDetent = SheetDetent.Hidden)
    val scope = rememberCoroutineScope()
    val hide: () -> Unit = { scope.launch { sheetState.animateTo(SheetDetent.Hidden) } }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(sheetState) {
        launch { sheetState.animateTo(SheetDetent.FullyExpanded) }
        snapshotFlow { sheetState.currentDetent }
            .dropWhile { it == SheetDetent.Hidden }
            .first { it == SheetDetent.Hidden }
        currentOnDismiss()
    }
    UnstyledModalBottomSheet(state = sheetState, overlay = { Scrim(scrimColor = scrimColor()) }) {
        Sheet(Modifier.fillMaxWidth()) {
            ModalBackHandler(hide)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .semantics { paneTitle = title }
                        .widthIn(max = Sizes.sheet)
                        .fillMaxWidth()
                        .clip(Corners.sheet)
                        .background(palette.surface)
                        // The navigation bar, and the keyboard when a field in the sheet opens one.
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        .padding(start = Spacing.space16, end = Spacing.space16, bottom = Spacing.space24),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DragIndication(
                        Modifier
                            .size(Sizes.iconButton, Spacing.space24)
                            .semantics { contentDescription = dragHandle }
                            .drawBehind {
                                val handle = Size(Sizes.handleWidth.toPx(), Sizes.handleHeight.toPx())
                                drawRoundRect(
                                    color = palette.textDim.copy(alpha = Opacity.DISABLED),
                                    topLeft = Offset(
                                        (size.width - handle.width) / 2,
                                        (size.height - handle.height) / 2,
                                    ),
                                    size = handle,
                                    cornerRadius = CornerRadius(handle.height / 2),
                                )
                            },
                    )
                    Column(
                        Modifier.fillMaxWidth().padding(top = Spacing.space8),
                        verticalArrangement = Arrangement.spacedBy(Spacing.space20),
                    ) {
                        content(hide)
                    }
                }
            }
        }
    }
}
