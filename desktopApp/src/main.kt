package dev.justinsmith.interval

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.app_name
import org.jetbrains.compose.resources.stringResource
import java.awt.Dimension

/** Opens wide enough for the side-by-side layouts; resizing below the minimum would clip the phone layouts. */
private val InitialSize = DpSize(1024.dp, 720.dp)
private val MinimumSize = Dimension(360, 560)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(size = InitialSize),
        title = stringResource(Res.string.app_name),
    ) {
        LaunchedEffect(Unit) { window.minimumSize = MinimumSize }
        App()
    }
}
