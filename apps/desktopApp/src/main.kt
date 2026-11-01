package dev.justinsmith.interval

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.justinsmith.interval.di.drainStorage
import dev.justinsmith.interval.di.initAppKoin
import dev.justinsmith.interval.di.stopAppKoin
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.app_icon
import dev.justinsmith.interval.resources.app_name
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.awt.Dimension
import kotlin.system.exitProcess

/** Opens wide enough for the side-by-side layouts; resizing below the minimum would clip the phone layouts. */
private val InitialSize = DpSize(1024.dp, 720.dp)
private val MinimumSize = Dimension(360, 560)

fun main() {
    val koin = initAppKoin()
    application(exitProcessOnExit = false) {
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(size = InitialSize),
            title = stringResource(Res.string.app_name),
            icon = painterResource(Res.drawable.app_icon),
        ) {
            LaunchedEffect(Unit) { window.minimumSize = MinimumSize }
            App()
        }
    }
    // The window has gone and the process ends with it: land the writes still queued (a setting changed a moment
    // ago) before the container closes and cancels them. Here, off the UI thread, for a bounded time.
    runBlocking { koin.drainStorage() }
    stopAppKoin()
    exitProcess(0)
}
