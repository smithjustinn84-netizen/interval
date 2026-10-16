package dev.justinsmith.interval.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.PhaseColors
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing

/** End pauses the workout and asks before throwing it away (see [WorkoutScreen]). */
@Composable
internal fun EndPill(onEnd: () -> Unit, modifier: Modifier = Modifier) {
    PillButton(text = "End", onClick = onEnd, description = "End workout", modifier = modifier)
}

/** How far through the current interval, as a bar. */
@Composable
internal fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Sizes.progress)
            .clip(Corners.full)
            .background(PhaseColors.progressTrack),
    ) {
        if (progress > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(Corners.full)
                    .background(PhaseColors.onPhase),
            )
        }
    }
}

@Composable
internal fun BottomControls(
    state: WorkoutUiState,
    onTogglePause: () -> Unit,
    onBack: () -> Unit,
    onAddTime: () -> Unit,
) {
    val height = Sizes.control
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.space10)) {
        BigButton(
            "Back",
            onBack,
            Modifier.width(Sizes.controlSide).height(height),
            description = "Back to previous interval",
        )
        PrimaryButton(state, onTogglePause, Modifier.weight(1f).height(height))
        BigButton(
            "+10s",
            onAddTime,
            Modifier.width(Sizes.controlSide).height(height),
            description = "Add ten seconds",
        )
    }
}

/** The white Pause / Resume button. */
@Composable
internal fun PrimaryButton(state: WorkoutUiState, onTogglePause: () -> Unit, modifier: Modifier = Modifier) {
    BigButton(
        if (state.running) "Pause" else "Resume",
        onTogglePause,
        modifier,
        background = PhaseColors.onPhase,
        content = PhaseColors.ink,
        style = IntervalTheme.type.primaryControl,
    )
}

@Composable
internal fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = PhaseColors.glass,
    content: Color = PhaseColors.onPhase,
    style: TextStyle = IntervalTheme.type.control,
    description: String? = null,
) {
    Box(
        modifier
            .clip(Corners.radius16)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = content)
    }
}

@Composable
internal fun PillButton(
    text: String,
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    background: Color = PhaseColors.glass,
    content: Color = PhaseColors.onPhase,
) {
    Box(
        modifier
            .sizeIn(minWidth = Sizes.pillWidth, minHeight = Sizes.pillHeight)
            .clip(Corners.radius12)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = Spacing.space18),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = IntervalTheme.type.cardTitle,
            color = content,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
