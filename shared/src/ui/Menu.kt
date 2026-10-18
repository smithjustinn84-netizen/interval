package dev.justinsmith.interval.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.composeunstyled.AnchorAlignment
import com.composeunstyled.DropdownMenuPanel
import com.composeunstyled.UnstyledDropdownMenu
import com.composeunstyled.UnstyledDropdownMenuItem

/**
 * An overflow menu that opens from [anchor]: a flat surface panel set off by a line border, since
 * only Start workout is raised. Back, Esc and a tap outside close it.
 */
@Composable
fun IntervalMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    anchor: @Composable () -> Unit,
) {
    val palette = IntervalTheme.palette
    UnstyledDropdownMenu(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
        alignment = AnchorAlignment.End,
        sideOffset = Spacing.space4,
        panel = {
            DropdownMenuPanel(
                Modifier
                    .widthIn(min = Sizes.menu)
                    .clip(Corners.radius18)
                    .background(palette.surface)
                    .border(Sizes.border, palette.line, Corners.radius18)
                    .padding(Spacing.space8),
            ) {
                ModalBackHandler { onExpandedChange(false) }
                items()
            }
        },
        anchor = anchor,
    )
}

/** A row of an [IntervalMenu]; it closes the menu, then runs [onClick]. */
@Composable
fun IntervalMenuItem(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    UnstyledDropdownMenuItem(
        onClick = onClick,
        interactionSource = interaction,
        indication = LocalIndication.current,
        modifier = modifier
            .semantics(mergeDescendants = true) { role = Role.Button }
            .fillMaxWidth()
            .clip(Corners.radius12)
            .background(if (focused || hovered) palette.surfaceVariant else Color.Transparent),
    ) {
        Box(
            Modifier.heightIn(min = Sizes.pillHeight).padding(horizontal = Spacing.space12),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text, style = IntervalTheme.type.label, color = palette.text)
        }
    }
}
