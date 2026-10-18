package dev.justinsmith.interval.ui

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusTarget
import com.composeunstyled.DialogPanel
import com.composeunstyled.Scrim
import com.composeunstyled.UnstyledDialog

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
    // The scrim fades up and the panel grows in over it. Leaving is instant, with no exit to animate:
    // a dialog leaves composition the moment its action runs, along with the state that showed it.
    UnstyledDialog(
        visible = true,
        onDismissRequest = onDismiss,
        overlay = {
            Scrim(scrimColor = scrimColor(), enter = fadeIn(tween(Motion.ENTER_MILLIS)), exit = ExitTransition.None)
        },
    ) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.space24), contentAlignment = Alignment.Center) {
            DialogPanel(
                Modifier
                    .widthIn(max = Sizes.content)
                    .clip(Corners.radius18)
                    .background(palette.surface),
                paneTitle = title,
                enter = enterGrowing(),
                exit = ExitTransition.None,
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

/** Asks before an action that can't be undone: title, one line of text, a dismiss and a confirm action. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    IntervalDialog(
        title = title,
        onDismiss = onDismiss,
        actions = {
            DialogButton(dismiss, onClick = onDismiss, primary = false)
            DialogButton(confirm, onClick = onConfirm, primary = true)
        },
    ) {
        Text(text, style = IntervalTheme.type.body, color = IntervalTheme.palette.textDim)
    }
}
