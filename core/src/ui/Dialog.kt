package dev.justinsmith.interval.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.composeunstyled.DialogPanel
import com.composeunstyled.Scrim
import com.composeunstyled.UnstyledDialog

/**
 * A centred dialog drawn from Interval tokens: a [title], its [body] and a row of [actions] that
 * share the width. Back, Esc and a tap outside the panel all call [onDismiss].
 *
 * The scrim fades up and the panel grows in over it; leaving, the panel shrinks back as it fades and
 * the scrim fades with it. An action calls the `leave` it is given with what it does, which runs once
 * the dialog has gone: the state that showed the dialog flips then, not while it is still on screen.
 */
@Composable
fun IntervalDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.(leave: (then: () -> Unit) -> Unit) -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    val palette = IntervalTheme.palette
    var visible by remember { mutableStateOf(true) }
    // Plain, not state: the panel may be disposed in the very composition that hides it, before any
    // recomposition could hand it a newer value.
    val exit = remember { DialogExit() }
    val leave: (() -> Unit) -> Unit = { then ->
        exit.then = then
        visible = false
    }
    val dismiss = { leave(onDismiss) }
    UnstyledDialog(
        visible = visible,
        onDismissRequest = dismiss,
        overlay = {
            Scrim(
                scrimColor = scrimColor(),
                enter = fadeIn(tween(Motion.ENTER_MILLIS)),
                exit = fadeOut(tween(Motion.EXIT_MILLIS)),
            )
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
                exit = exitShrinkingBack(),
            ) {
                // The panel leaves composition once its exit has played: the action runs then.
                DisposableEffect(Unit) { onDispose { exit.then?.invoke() } }
                ModalBackHandler(dismiss)
                Column(
                    Modifier.holdInitialFocus().padding(
                        start = Spacing.space20,
                        top = Spacing.space24,
                        end = Spacing.space20,
                        bottom = Spacing.space20,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space12),
                ) {
                    Text(
                        title,
                        style = IntervalTheme.type.value,
                        color = palette.text,
                        modifier = Modifier.semantics { heading() },
                    )
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.space12),
                        content = body,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = Spacing.space16),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
                    ) {
                        actions(leave)
                    }
                }
            }
        }
    }
}

/** What a dialog does once it has gone: set by the action that closed it, run as the panel leaves. */
private class DialogExit {
    var then: (() -> Unit)? = null
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
    destructive: Boolean = false,
) {
    IntervalDialog(
        title = title,
        onDismiss = onDismiss,
        actions = { leave ->
            DialogButton(dismiss, onClick = { leave(onDismiss) }, primary = false)
            DialogButton(confirm, onClick = { leave(onConfirm) }, primary = true, destructive = destructive)
        },
    ) {
        Text(text, style = IntervalTheme.type.body, color = IntervalTheme.palette.textDim)
    }
}
