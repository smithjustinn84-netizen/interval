@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.format
import kotlinx.coroutines.launch

/** How each [ConfigField] is named and described wherever it is edited. */
val ConfigField.label: String
    get() = when (this) {
        ConfigField.Prep -> "Get ready"
        ConfigField.Work -> "Work"
        ConfigField.Rest -> "Rest"
        ConfigField.Rounds -> "Rounds"
    }

val ConfigField.hint: String
    get() = when (this) {
        ConfigField.Prep -> "Countdown before the first round"
        ConfigField.Work -> "Effort interval"
        ConfigField.Rest -> "Recovery between rounds"
        ConfigField.Rounds -> "Work intervals in the set"
    }

/** The phase stripe that ties a field to its workout colour; Rounds takes the accent. */
@Composable
@ReadOnlyComposable
fun ConfigField.stripe(): Color = when (this) {
    ConfigField.Prep -> PhaseColors.prep
    ConfigField.Work -> PhaseColors.work
    ConfigField.Rest -> PhaseColors.rest
    ConfigField.Rounds -> IntervalTheme.palette.accent
}

/** The pale wash of the field's colour behind its value. */
@Composable
@ReadOnlyComposable
fun ConfigField.tint(): Color = IntervalTheme.palette.tint(stripe())

/** The field's value on its [tint]: its own colour, except Get ready, whose amber is darkened to read. */
@Composable
@ReadOnlyComposable
fun ConfigField.valueColor(): Color =
    if (this == ConfigField.Prep) IntervalTheme.palette.onPrepTint else stripe()

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
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = Corners.sheet,
        containerColor = palette.surface,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = Spacing.space8)
                    .size(Sizes.handleWidth, Sizes.handleHeight)
                    .clip(Corners.full)
                    .background(palette.textDim.copy(alpha = Opacity.DISABLED)),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = Spacing.space16, end = Spacing.space16, bottom = Spacing.space24),
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
            AccentButton(
                "Done",
                onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
                modifier = Modifier.fillMaxWidth(),
            )
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
        Text(
            if (field ==
                ConfigField.Rounds
            ) {
                "Hold − or + to change faster."
            } else {
                "Steps of ${field.step}s. Hold to change faster."
            },
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
                    QuickPick(field, field.format(pick), selected = pick == value, {
                        onPick(pick)
                    }, Modifier.weight(1f))
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
