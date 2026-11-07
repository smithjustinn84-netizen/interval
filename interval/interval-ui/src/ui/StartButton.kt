package dev.justinsmith.interval.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import dev.justinsmith.interval.ui.resources.Res
import dev.justinsmith.interval.ui.resources.common_start_workout
import org.jetbrains.compose.resources.stringResource

/**
 * The one raised, accent-filled action of a screen: Start workout, or what else it starts by its [label]. Its
 * shadow is the accent itself, so the button glows rather than sits in a grey smudge. Pressed, it launches: one
 * ring of the accent sets out from its centre across [IntervalSizes.launchBurst], past the page round it, in the
 * time the workout takes to rise ([Motion.LAUNCH_MILLIS]), so the workout's ring reads as what the button sent up.
 * [onClick] fires at once, with the ring; every press launches again.
 */
@Composable
fun StartButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(Res.string.common_start_workout),
) {
    val palette = IntervalTheme.palette
    val shape = Corners.radius18
    val glow = palette.accent.copy(alpha = Opacity.START_SHADOW)
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    var launches by remember { mutableIntStateOf(0) }
    Box(modifier) {
        // Behind the button, and larger than it: the ring is centred on the button and drawn out past its bounds.
        if (launches > 0) {
            key(launches) {
                Burst(
                    listOf(palette.accent),
                    Modifier.matchParentSize().requiredSize(IntervalSizes.launchBurst),
                    stroke = IntervalSizes.launchStroke,
                    durationMillis = Motion.LAUNCH_MILLIS,
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = IntervalSizes.start)
                .pressScale(interaction)
                .shadow(elevation = IntervalSizes.startElevation, shape = shape, ambientColor = glow, spotColor = glow)
                .clip(shape)
                .background(palette.accent)
                .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    launches++
                    onClick()
                }
                .padding(horizontal = Spacing.space20, vertical = Spacing.space8),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space12, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                IntervalIcons.Play,
                contentDescription = null,
                tint = palette.onAccent,
                modifier = Modifier.size(IntervalSizes.playIcon),
            )
            Text(
                label,
                style = IntervalTheme.type.value,
                color = palette.onAccent,
            )
        }
    }
}
