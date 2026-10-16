package dev.justinsmith.interval.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.composeunstyled.AnchorAlignment
import com.composeunstyled.DialogPanel
import com.composeunstyled.DropdownMenuPanel
import com.composeunstyled.Scrim
import com.composeunstyled.UnstyledDialog
import com.composeunstyled.UnstyledDropdownMenu
import com.composeunstyled.UnstyledDropdownMenuItem

/** The dim laid over the app behind a dialog or sheet: `ink`, so it darkens without greying. */
@Composable
@ReadOnlyComposable
fun scrimColor(): Color = PhaseColors.ink.copy(alpha = Opacity.SCRIM)

/**
 * Closes a dialog or sheet on the system back gesture. Unstyled dismisses on the Back and Esc keys,
 * but Android 13+ delivers back to the modal's window as a back event, never as a key.
 */
@Composable
fun ModalBackHandler(onBack: () -> Unit) {
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = onBack)
}

/**
 * A centred dialog drawn from Interval tokens: a [title], its [body] and a row of [actions] that
 * share the width. Back, Esc and a tap outside the panel all call [onDismiss].
 */
@Composable
fun IntervalDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    val palette = IntervalTheme.palette
    UnstyledDialog(visible = true, onDismissRequest = onDismiss, overlay = { Scrim(scrimColor = scrimColor()) }) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.space24), contentAlignment = Alignment.Center) {
            DialogPanel(
                Modifier
                    .widthIn(max = Sizes.content)
                    .clip(Corners.radius18)
                    .background(palette.surface),
                paneTitle = title,
            ) {
                ModalBackHandler(onDismiss)
                // The panel moves focus to its first focus target when it opens. Be that target, so a
                // text field waits for a tap: on iOS a field focused this early gets no keyboard, and
                // what is typed into it surfaces later, doubled.
                Column(
                    Modifier.focusTarget().padding(
                        start = Spacing.space20,
                        top = Spacing.space24,
                        end = Spacing.space20,
                        bottom = Spacing.space20,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space12),
                ) {
                    Text(title, style = IntervalTheme.type.value, color = palette.text)
                    body()
                    Row(
                        Modifier.fillMaxWidth().padding(top = Spacing.space16),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
                        content = actions,
                    )
                }
            }
        }
    }
}

/** One of a dialog's equal-width actions: the confirming one in accent, the other on a quiet fill. */
@Composable
fun RowScope.DialogButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ModalButton(
        text = text,
        onClick = onClick,
        primary = primary,
        style = IntervalTheme.type.control,
        padding = Spacing.space12,
        modifier = modifier.weight(1f),
        enabled = enabled,
    )
}

/** A full-width accent button for a sheet's confirming action. */
@Composable
fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    ModalButton(
        text = text,
        onClick = onClick,
        primary = true,
        style = IntervalTheme.type.value,
        padding = Spacing.space24,
        modifier = modifier,
        enabled = enabled,
    )
}

/** The button recipe dialogs and sheets share: accent when [primary], a quiet fill otherwise, faded while disabled. */
@Composable
private fun ModalButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean,
    style: TextStyle,
    padding: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = IntervalTheme.palette
    val fill = if (primary) palette.accent else palette.surfaceVariant
    Box(
        modifier
            .heightIn(min = Sizes.save)
            .clip(Corners.radius18)
            .background(if (enabled) fill else fill.copy(alpha = Opacity.DISABLED))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = if (primary) palette.onAccent else palette.text)
    }
}

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
