package dev.justinsmith.interval.setup

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.data.CueSettings
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.Opacity
import dev.justinsmith.interval.ui.PhaseColors
import dev.justinsmith.interval.ui.Radius
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import kotlinx.coroutines.delay
import org.koin.compose.currentKoinScope

/** Resolves the screen's ViewModel, scoped to its back-stack entry, and binds it to [SetupScreen]. */
@Composable
fun SetupRoute(onStart: () -> Unit) {
    val scope = currentKoinScope()
    val viewModel = viewModel { SetupViewModel(scope.get(), scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SetupScreen(
        state = state,
        onStep = viewModel::step,
        onApplyPreset = viewModel::applyPreset,
        onSavePreset = viewModel::savePreset,
        onDeletePreset = viewModel::deletePreset,
        onToggle = viewModel::toggle,
        onClearHistory = viewModel::clearHistory,
        onStart = onStart,
    )
}

@Composable
fun SetupScreen(
    state: SetupUiState,
    onStep: (ConfigField, Int) -> Unit,
    onApplyPreset: (Preset) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (Preset) -> Unit,
    onToggle: (CueToggle) -> Unit,
    onClearHistory: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(IntervalTheme.palette.background)) {
        if (maxWidth > maxHeight) {
            LandscapeSetup(
                state,
                onStep,
                onApplyPreset,
                onSavePreset,
                onDeletePreset,
                onToggle,
                onClearHistory,
                onStart,
            )
        } else {
            PortraitSetup(
                state,
                onStep,
                onApplyPreset,
                onSavePreset,
                onDeletePreset,
                onToggle,
                onClearHistory,
                onStart,
            )
        }
    }
}

@Composable
private fun BoxScope.PortraitSetup(
    state: SetupUiState,
    onStep: (ConfigField, Int) -> Unit,
    onApplyPreset: (Preset) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (Preset) -> Unit,
    onToggle: (CueToggle) -> Unit,
    onClearHistory: () -> Unit,
    onStart: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .widthIn(max = Sizes.content)
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        // The bottom padding leaves room for the floating Start button.
        contentPadding = PaddingValues(
            start = Spacing.space16,
            end = Spacing.space16,
            top = Spacing.space16,
            bottom = Spacing.space112,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.space10),
    ) {
        item { Header(total = state.total, Modifier.padding(top = Spacing.space8, bottom = Spacing.space8)) }
        items(state.presets.chunked(2)) { row -> PresetRow(row, onApplyPreset, onDeletePreset) }
        item { SavePreset(enabled = state.canSavePreset, onSave = onSavePreset) }
        item { Spacer(Modifier.height(Spacing.space4)) }
        settings(state, onStep, onToggle, onClearHistory)
    }
    StartButton(
        onClick = onStart,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .widthIn(max = Sizes.content)
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(Spacing.space16),
    )
}

/**
 * Sideways: header, presets and Start on the left, so all four steppers sit beside Start without
 * scrolling; the settings scroll on the right. Start is pinned under the presets rather than floating.
 */
@Composable
private fun LandscapeSetup(
    state: SetupUiState,
    onStep: (ConfigField, Int) -> Unit,
    onApplyPreset: (Preset) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (Preset) -> Unit,
    onToggle: (CueToggle) -> Unit,
    onClearHistory: () -> Unit,
    onStart: () -> Unit,
) {
    Row(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        // On a phone the two columns fill the width; on a tablet or desktop window they stay a
        // centred pair instead of stretching the field cards across the screen.
        horizontalArrangement = Arrangement.spacedBy(Spacing.space16, Alignment.CenterHorizontally),
    ) {
        Column(
            Modifier
                .width(Sizes.setupColumnLandscape)
                .fillMaxHeight()
                .padding(start = Spacing.space20, top = Spacing.space16, bottom = Spacing.space16),
            verticalArrangement = Arrangement.spacedBy(Spacing.space10),
        ) {
            // Custom presets add rows, so this part scrolls on its own and Start never moves.
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.space10),
            ) {
                Header(total = state.total, Modifier.padding(bottom = Spacing.space4))
                state.presets.chunked(2).forEach { row -> PresetRow(row, onApplyPreset, onDeletePreset) }
                SavePreset(enabled = state.canSavePreset, onSave = onSavePreset)
            }
            StartButton(onClick = onStart, modifier = Modifier.fillMaxWidth())
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(max = Sizes.content + Spacing.space20)
                .fillMaxHeight(),
            contentPadding = PaddingValues(end = Spacing.space20, top = Spacing.space16, bottom = Spacing.space16),
            verticalArrangement = Arrangement.spacedBy(Spacing.space10),
        ) {
            settings(state, onStep, onToggle, onClearHistory)
        }
    }
}

/** The field cards, cue toggles and history, in both layouts. */
private fun LazyListScope.settings(
    state: SetupUiState,
    onStep: (ConfigField, Int) -> Unit,
    onToggle: (CueToggle) -> Unit,
    onClearHistory: () -> Unit,
) {
    item {
        FieldCard(
            label = "Get ready",
            hint = "Countdown before the first round",
            value = formatClock(state.config.prepSeconds),
            stripe = PhaseColors.prep,
            field = ConfigField.Prep,
            current = state.config.prepSeconds,
            noun = "prepare time",
            onStep = onStep,
        )
    }
    item {
        FieldCard(
            label = "Work",
            hint = "Effort interval",
            value = formatClock(state.config.workSeconds),
            stripe = PhaseColors.work,
            field = ConfigField.Work,
            current = state.config.workSeconds,
            noun = "work time",
            onStep = onStep,
        )
    }
    item {
        FieldCard(
            label = "Rest",
            hint = "Recovery between rounds",
            value = formatClock(state.config.restSeconds),
            stripe = PhaseColors.rest,
            field = ConfigField.Rest,
            current = state.config.restSeconds,
            noun = "rest time",
            onStep = onStep,
        )
    }
    item {
        FieldCard(
            label = "Rounds",
            hint = "Work intervals to complete",
            value = state.config.rounds.toString(),
            stripe = IntervalTheme.palette.accent,
            field = ConfigField.Rounds,
            current = state.config.rounds,
            noun = "rounds",
            onStep = onStep,
        )
    }
    item {
        ToggleRow("Sound cues", "Beeps for countdown and phase changes", state.cues.sound) {
            onToggle(CueToggle.Sound)
        }
    }
    item {
        ToggleRow("Voice cues", "Says “Work”, “Rest”, “Last round”", state.cues.voice) {
            onToggle(CueToggle.Voice)
        }
    }
    item {
        ToggleRow("Halfway cue", "Extra beep mid-interval, over 30s", state.cues.halfway) {
            onToggle(CueToggle.Halfway)
        }
    }
    item {
        ToggleRow("Vibrate on phase change", null, state.cues.vibrate) { onToggle(CueToggle.Vibrate) }
    }
    item { History(items = state.history, onClear = onClearHistory) }
}

@Composable
private fun Header(total: String, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text("Interval Timer", style = IntervalTheme.type.screenTitle, color = palette.text)
        Text(
            buildAnnotatedString {
                append("Total ")
                withStyle(SpanStyle(color = palette.text, fontWeight = FontWeight.SemiBold)) { append(total) }
            },
            style = IntervalTheme.type.total,
            color = palette.textDim,
            modifier = Modifier.padding(bottom = Spacing.space6),
        )
    }
}

/** Two presets side by side; an odd one out keeps half the row. */
@Composable
private fun PresetRow(row: List<PresetItem>, onApply: (Preset) -> Unit, onDelete: (Preset) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        row.forEach { item ->
            PresetCard(
                item = item,
                onClick = { onApply(item.preset) },
                onDelete = { onDelete(item.preset) },
                modifier = Modifier.weight(1f),
            )
        }
        if (row.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PresetCard(
    item: PresetItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    val shape = Corners.radius14
    Box(
        modifier
            .clip(shape)
            .background(if (item.selected) palette.accentTint else palette.surface)
            .border(Sizes.border, if (item.selected) palette.accent else palette.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.space14, vertical = Spacing.space12),
    ) {
        Column(Modifier.padding(end = if (item.custom) Spacing.space24 else 0.dp)) {
            Text(item.preset.name, style = IntervalTheme.type.cardTitle, color = palette.text, maxLines = 1)
            Text(item.description, style = IntervalTheme.type.presetDescription, color = palette.textDim)
        }
        if (item.custom) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(Sizes.delete)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onDelete)
                    .semantics { contentDescription = "Delete preset ${item.preset.name}" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "×",
                    style = IntervalTheme.type.deleteGlyph,
                    color = palette.textDim,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

/** The empty save slot's dashed outline, in px: 12 on, 8 off. */
private val SaveSlotDash = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))

@Composable
private fun SavePreset(enabled: Boolean, onSave: (String) -> Unit) {
    val palette = IntervalTheme.palette
    var editing by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    if (editing) {
        val save = {
            onSave(name)
            name = ""
            editing = false
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(IntervalRepository.MAX_PRESET_NAME) },
                placeholder = { Text("Preset name") },
                singleLine = true,
                shape = Corners.radius14,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = palette.surface,
                    unfocusedContainerColor = palette.surface,
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = palette.line,
                ),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { save() },
                shape = Corners.radius14,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
                modifier = Modifier.heightIn(min = Sizes.save),
            ) { Text("Save", style = IntervalTheme.type.buttonLabel) }
            TextButton(onClick = {
                name = ""
                editing = false
            }) { Text("Cancel", color = palette.textDim) }
        }
    } else {
        val radius = Radius.radius14
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.pillHeight)
                .alpha(if (enabled) 1f else Opacity.DISABLED)
                .clip(Corners.radius14)
                .drawBehind {
                    val stroke = Sizes.border.toPx()
                    drawRoundRect(
                        color = palette.line,
                        cornerRadius = CornerRadius(radius.toPx()),
                        style = Stroke(width = stroke, pathEffect = SaveSlotDash),
                    )
                }
                .clickable(enabled = enabled, role = Role.Button) { editing = true },
            contentAlignment = Alignment.Center,
        ) {
            Text("+ Save current settings as a preset", style = IntervalTheme.type.cardTitle, color = palette.textDim)
        }
    }
}

@Composable
private fun FieldCard(
    label: String,
    hint: String,
    value: String,
    stripe: Color,
    field: ConfigField,
    current: Int,
    noun: String,
    onStep: (ConfigField, Int) -> Unit,
) {
    val palette = IntervalTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(Corners.radius18)
            .background(palette.surface),
    ) {
        Box(Modifier.width(Sizes.stripe).fillMaxHeight().background(stripe))
        Row(
            Modifier.weight(1f).padding(Spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = IntervalTheme.type.cardTitle, color = palette.text)
                Text(hint, style = IntervalTheme.type.hint, color = palette.textDim)
            }
            RepeatButton(
                symbol = "−",
                description = if (field == ConfigField.Rounds) "Fewer rounds" else "Less $noun",
                enabled = current > field.range.first,
                onStep = { onStep(field, -1) },
            )
            Text(
                value,
                style = IntervalTheme.type.stepperValue,
                color = palette.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = Sizes.valueMin),
            )
            RepeatButton(
                symbol = "+",
                description = if (field == ConfigField.Rounds) "More rounds" else "More $noun",
                enabled = current < field.range.last,
                onStep = { onStep(field, 1) },
            )
        }
    }
}

/** A stepper button that repeats while held: one step on tap, then a fast run after a short hold. */
@Composable
private fun RepeatButton(
    symbol: String,
    description: String,
    enabled: Boolean,
    onStep: () -> Unit,
) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val step by rememberUpdatedState(onStep)
    // Set once the hold starts repeating, so the click that ends the hold doesn't add one more step.
    // Cleared on every new press or cancelled press: a hold that ends without a click (dragged off,
    // or the field hit its limit) must not swallow the next tap.
    var repeated by remember { mutableStateOf(false) }
    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            if (it is PressInteraction.Press || it is PressInteraction.Cancel) repeated = false
        }
    }
    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled) {
            delay(Motion.STEPPER_HOLD_MILLIS)
            repeated = true
            while (true) {
                step()
                delay(Motion.STEPPER_REPEAT_MILLIS)
            }
        }
    }
    Box(
        Modifier
            .size(Sizes.stepper)
            .clip(Corners.radius12)
            .background(palette.surfaceVariant)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
            ) {
                if (!repeated) step()
                repeated = false
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            style = IntervalTheme.type.stepperGlyph,
            color = if (enabled) palette.text else palette.textDim.copy(alpha = Opacity.DISABLED_GLYPH),
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val palette = IntervalTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.toggleRow)
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() })
            .padding(horizontal = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = IntervalTheme.type.body, color = palette.text)
            if (subtitle != null) Text(subtitle, style = IntervalTheme.type.hint, color = palette.textDim)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = palette.accent,
                checkedThumbColor = palette.onAccent,
                checkedBorderColor = palette.accent,
                uncheckedTrackColor = palette.line,
                uncheckedThumbColor = palette.onAccent,
                uncheckedBorderColor = palette.line,
            ),
        )
    }
}

@Composable
private fun History(items: List<HistoryItem>, onClear: () -> Unit) {
    val palette = IntervalTheme.palette
    Column(Modifier.padding(top = Spacing.space18), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Recent workouts", style = IntervalTheme.type.sectionTitle, color = palette.text)
            if (items.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text("Clear", style = IntervalTheme.type.buttonLabel, color = palette.textDim)
                }
            }
        }
        Column(Modifier.fillMaxWidth().clip(Corners.radius18).background(palette.surface)) {
            if (items.isEmpty()) {
                Text(
                    "Finished workouts show up here.",
                    style = IntervalTheme.type.body,
                    color = palette.textDim,
                    modifier = Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space14),
                )
            }
            items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = palette.line)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.space16, vertical = Spacing.space12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = IntervalTheme.type.total, color = palette.text)
                        Text(item.finishedAt, style = IntervalTheme.type.hint, color = palette.textDim)
                    }
                    Text(item.total, style = IntervalTheme.type.sectionTitle, color = palette.text)
                }
            }
        }
    }
}

@Composable
private fun StartButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val shape = Corners.radius18
    Box(
        modifier
            .height(Sizes.start)
            .shadow(elevation = Sizes.startElevation, shape = shape)
            .clip(shape)
            .background(palette.accent)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("Start workout", style = IntervalTheme.type.startLabel, color = palette.onAccent)
    }
}

@Composable
private fun SetupPreview() {
    val config = IntervalConfig()
    IntervalTheme {
        SetupScreen(
            state = SetupUiState(
                config = config,
                cues = CueSettings(halfway = false),
                total = formatClock(config.totalSeconds),
                presets = builtInPresets.map {
                    PresetItem(it, it.config.description, custom = false, selected = false)
                },
                canSavePreset = true,
                history = listOf(HistoryItem("Tabata · 20s on · 10s off · ×8", "Wed, Oct 14, 7:12 PM", "4:00")),
            ),
            onStep = { _, _ -> },
            onApplyPreset = {},
            onSavePreset = {},
            onDeletePreset = {},
            onToggle = {},
            onClearHistory = {},
            onStart = {},
        )
    }
}

@Preview
@Composable
private fun SetupScreenPreview() {
    SetupPreview()
}

@Preview(widthDp = 844, heightDp = 390)
@Composable
private fun SetupScreenLandscapePreview() {
    SetupPreview()
}
