package dev.justinsmith.interval.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.window.core.layout.WindowSizeClass
import com.composeunstyled.DialogPanel
import com.composeunstyled.DragIndication
import com.composeunstyled.Scrim
import com.composeunstyled.Sheet
import com.composeunstyled.SheetDetent
import com.composeunstyled.UnstyledDialog
import com.composeunstyled.UnstyledModalBottomSheet
import com.composeunstyled.rememberModalBottomSheetState
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.sheet_close_cd
import dev.justinsmith.interval.resources.value_sheet_drag_handle_cd
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * A modal sheet drawn from Interval tokens: the surface, and [content] spaced as a form under a way
 * out. The content gets [hide] for its own way out (Done, Cancel, Save). Every way out, back, Esc
 * and a tap on the scrim included, ends with the sheet slid away, and only then does the caller hear
 * [onDismiss]. Screen readers announce it by [title].
 *
 * On a phone it rises from the bottom under a drag handle, and a swipe down closes it too. A window
 * at least Material's medium width and height (a tablet, a desktop, an unfolded foldable) gets a
 * side sheet instead: full height at the end edge, under a close button. Either one scrolls content
 * taller than its room (a phone on its side, or the keyboard up over the preset editor), so its last
 * button is always reachable.
 */
@Composable
fun IntervalSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(hide: () -> Unit) -> Unit,
) {
    // Read here, not inside the modal, which may be a window of its own. Kept for as long as the
    // sheet is open, so resizing the window mid-edit does not swap the sheet and lose its content.
    val windowSize = currentWindowAdaptiveInfoV2().windowSizeClass
    val beside = remember {
        windowSize.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) &&
            windowSize.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    }
    if (beside) SideSheet(title, onDismiss, content) else BottomSheet(title, onDismiss, content)
}

/** The phone's sheet: it rises whole from the bottom, stops under the status bar and above the keyboard. */
@Composable
private fun BottomSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(hide: () -> Unit) -> Unit,
) {
    val palette = IntervalTheme.palette
    val dragHandle = stringResource(Res.string.value_sheet_drag_handle_cd)
    // No half-open stop: a sheet opens whole, and scrolls inside when it has less room than it needs.
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
            Box(Modifier.fillMaxWidth().withinBarsAndKeyboard(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .semantics { paneTitle = title }
                        .widthIn(max = Sizes.sheet)
                        .fillMaxWidth()
                        .clip(Corners.sheet)
                        .background(palette.surface)
                        // The navigation bar. The keyboard's inset never reaches here: the modal
                        // sheet lifts itself over the keyboard and keeps that inset from its content.
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
                        Modifier.fillMaxWidth().padding(top = Spacing.space8).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.space20),
                    ) {
                        content(hide)
                    }
                }
            }
        }
    }
}

/**
 * The wide window's sheet: full height at the end edge, sliding in over the scrim. It has no drag
 * handle, so a close button heads it; the safe-drawing inset keeps its content clear of the bars
 * and the keyboard.
 */
@Composable
private fun SideSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(hide: () -> Unit) -> Unit,
) {
    val palette = IntervalTheme.palette
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Opened after the first frame, so the slide in plays.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val hide: () -> Unit = { visible = false }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    UnstyledDialog(
        visible = visible,
        onDismissRequest = hide,
        overlay = {
            Scrim(
                scrimColor = scrimColor(),
                enter = fadeIn(tween(Motion.ENTER_MILLIS)),
                exit = fadeOut(tween(Motion.EXIT_MILLIS)),
            )
        },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            DialogPanel(
                Modifier
                    .fillMaxHeight()
                    .width(Sizes.sideSheet)
                    .clip(Corners.sideSheet)
                    .background(palette.surface),
                paneTitle = title,
                enter = enterFromEnd(rtl),
                exit = exitToEnd(rtl),
            ) {
                ModalBackHandler(hide)
                // The panel leaves only once its slide out has played: that is when the caller hears.
                // A caller that drops the sheet while it is open has closed it already.
                DisposableEffect(Unit) { onDispose { if (!visible) currentOnDismiss() } }
                // The panel moves focus to its first focus target when it opens. Be that target, so a
                // text field waits for a tap: on iOS a field focused this early gets no keyboard, and
                // what is typed into it surfaces later, doubled.
                Column(
                    Modifier
                        .focusTarget()
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End),
                        )
                        .padding(
                            start = Spacing.space16,
                            top = Spacing.space8,
                            end = Spacing.space16,
                            bottom = Spacing.space24,
                        ),
                ) {
                    IconAction(
                        IntervalIcons.Close,
                        stringResource(Res.string.sheet_close_cd),
                        onClick = hide,
                        modifier = Modifier.align(Alignment.End),
                    )
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .padding(top = Spacing.space8)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.space20),
                    ) {
                        content(hide)
                    }
                }
            }
        }
    }
}

/**
 * Caps the height at the room between the window's top inset and the keyboard. The modal sheet
 * lifts itself by the keyboard's height but never shrinks for it, and pins its top at the window's
 * edge once it runs out of room: without this cap a tall sheet loses its bottom behind the keyboard,
 * and runs under the status bar.
 */
@Composable
private fun Modifier.withinBarsAndKeyboard(): Modifier {
    val bars = WindowInsets.safeDrawing
    val keyboard = WindowInsets.ime
    return layout { measurable, constraints ->
        val room = if (constraints.hasBoundedHeight) {
            (constraints.maxHeight - bars.getTop(this) - keyboard.getBottom(this))
                .coerceIn(constraints.minHeight, constraints.maxHeight)
        } else {
            constraints.maxHeight
        }
        val placeable = measurable.measure(constraints.copy(maxHeight = room))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}
