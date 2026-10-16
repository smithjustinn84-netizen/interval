package dev.justinsmith.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.composeunstyled.DragIndication
import com.composeunstyled.Scrim
import com.composeunstyled.Sheet
import com.composeunstyled.SheetDetent
import com.composeunstyled.UnstyledModalBottomSheet
import com.composeunstyled.rememberModalBottomSheetState
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.format
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val ConfigField.less: String
    get() = if (this == ConfigField.Rounds) "Fewer rounds" else "Less ${label.lowercase()} time"

private val ConfigField.more: String
    get() = if (this == ConfigField.Rounds) "More rounds" else "More ${label.lowercase()} time"

/**
 * The edit sheet for one value: big − / + steppers (hold to repeat), one-tap common values, the
 * session total it leads to, and Done. Changes apply as they are made; Done or a swipe down closes.
 */
@Composable
fun ValueSheet(
    field: ConfigField,
    value: Int,
    total: String?,
    onStep: (Int) -> Unit,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = IntervalTheme.palette
    // No half-open stop: the sheet is short enough to show whole.
    val sheetState = rememberModalBottomSheetState(initialDetent = SheetDetent.Hidden)
    val scope = rememberCoroutineScope()
    val hide: () -> Unit = { scope.launch { sheetState.animateTo(SheetDetent.Hidden) } }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(sheetState) {
        launch { sheetState.animateTo(SheetDetent.FullyExpanded) }
        // Every way out (Done, a swipe, back, Esc, a tap on the scrim) ends at Hidden; the caller hears
        // of it only then, so the sheet slides away instead of vanishing.
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
                        // Screen readers announce the sheet by its field as it opens.
                        .semantics { paneTitle = field.label }
                        .widthIn(max = Sizes.sheet)
                        .fillMaxWidth()
                        .clip(Corners.sheet)
                        .background(palette.surface)
                        .navigationBarsPadding()
                        .padding(start = Spacing.space16, end = Spacing.space16, bottom = Spacing.space24),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DragIndication(
                        Modifier
                            .size(Sizes.iconButton, Spacing.space24)
                            .semantics { contentDescription = "Drag handle" }
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
                        SheetHeader(field)
                        SheetStepper(field, value, onStep)
                        QuickPicks(field, value, onPick)
                        if (total != null) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.space4)) {
                                Text(
                                    "New total",
                                    style = IntervalTheme.type.total,
                                    color = palette.textDim,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(total, style = IntervalTheme.type.sectionTitle, color = palette.text)
                            }
                        }
                        AccentButton("Done", onClick = hide, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(field: ConfigField, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Box(Modifier.width(Sizes.stripe).height(Sizes.iconButton).clip(Corners.full).background(field.stripe()))
        Column {
            Text(field.label, style = IntervalTheme.type.startLabel, color = palette.text)
            Text(field.hint, style = IntervalTheme.type.hint, color = palette.textDim)
        }
    }
}

@Composable
private fun SheetStepper(field: ConfigField, value: Int, onStep: (Int) -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RepeatButton(
                "−",
                field.less,
                enabled = value > field.range.first,
                onStep = { onStep(-1) },
                modifier = Modifier.size(Sizes.sheetStepper),
            )
            Text(
                field.format(value),
                style = IntervalTheme.type.sheetValue,
                color = field.valueColor(),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            RepeatButton(
                "+",
                field.more,
                enabled = value < field.range.last,
                onStep = { onStep(1) },
                modifier = Modifier.size(Sizes.sheetStepper),
            )
        }
        val hint = if (field == ConfigField.Rounds) {
            "Hold − or + to change faster."
        } else {
            "Steps of ${field.step}s. Hold to change faster."
        }
        Text(
            hint,
            style = IntervalTheme.type.hint,
            color = palette.textDim,
            modifier = Modifier.padding(top = Spacing.space8),
        )
    }
}

@Composable
private fun QuickPicks(field: ConfigField, value: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        Text("Quick picks", style = IntervalTheme.type.groupLabel, color = IntervalTheme.palette.textDim)
        field.quickPicks.chunked(QUICK_PICKS_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                row.forEach { pick ->
                    QuickPick(
                        field,
                        field.format(pick),
                        selected = pick == value,
                        onClick = { onPick(pick) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private const val QUICK_PICKS_PER_ROW = 4

@Composable
private fun QuickPick(
    field: ConfigField,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    val shape = Corners.radius12
    Box(
        modifier
            .heightIn(min = Sizes.pillHeight)
            .clip(shape)
            .background(if (selected) field.tint() else palette.surface)
            .border(Sizes.border, if (selected) field.stripe() else palette.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = IntervalTheme.type.control, color = if (selected) field.valueColor() else palette.text)
    }
}
