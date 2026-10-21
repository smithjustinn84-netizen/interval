package dev.justinsmith.interval.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.common_start_workout
import org.jetbrains.compose.resources.stringResource

/**
 * The one raised, accent-filled action of a screen: Start workout. Its shadow is the accent itself,
 * so the button glows rather than sits in a grey smudge.
 */
@Composable
fun StartButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val shape = Corners.radius18
    val glow = palette.accent.copy(alpha = Opacity.START_SHADOW)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(Sizes.start)
            .pressScale(interaction)
            .shadow(elevation = Sizes.startElevation, shape = shape, ambientColor = glow, spotColor = glow)
            .clip(shape)
            .background(palette.accent)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button) {
                onClick()
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            IntervalIcons.Play,
            contentDescription = null,
            tint = palette.onAccent,
            modifier = Modifier.size(Sizes.playIcon),
        )
        Text(
            stringResource(Res.string.common_start_workout),
            style = IntervalTheme.type.value,
            color = palette.onAccent,
        )
    }
}
