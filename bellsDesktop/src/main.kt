package dev.justinsmith.bells

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.app_name
import org.jetbrains.compose.resources.stringResource
import java.awt.Dimension

private val InitialSize = DpSize(480.dp, 860.dp)
private val MinimumSize = Dimension(360, 560)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(size = InitialSize),
        title = stringResource(Res.string.app_name),
    ) {
        LaunchedEffect(Unit) { window.minimumSize = MinimumSize }
        BellsApp()
    }
}
