package dev.justinsmith.interval.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Whether the app has gone [Motion.IDLE_MILLIS] without a touch, click or key, as [WatchForIdle] sees it.
 * Decorative motion that would otherwise loop for as long as a screen is left open stops while it is true.
 * False where nothing watches, as in previews and tests.
 */
val LocalIdle = staticCompositionLocalOf<() -> Boolean> { { false } }

/**
 * Watches every input to [content], without consuming any, and provides [LocalIdle] to it. Read the
 * flag where it is used (an effect's key, a draw), so going idle recomposes only what asks.
 */
@Composable
fun WatchForIdle(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var idle by remember { mutableStateOf(false) }
    val inputs = remember { Channel<Unit>(Channel.CONFLATED) }
    LaunchedEffect(inputs) {
        inputs.receiveAsFlow().onStart { emit(Unit) }.collectLatest {
            idle = false
            delay(Motion.IDLE_MILLIS)
            idle = true
        }
    }
    Box(
        modifier
            .pointerInput(inputs) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        inputs.trySend(Unit)
                    }
                }
            }.onPreviewKeyEvent {
                inputs.trySend(Unit)
                false
            },
    ) {
        CompositionLocalProvider(LocalIdle provides { idle }, content = content)
    }
}
