package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** A file dialog, as Export's save is; the file is read off the window's thread once one is chosen. */
@Composable
internal actual fun rememberPlatformFileOpener(onOpen: (ShareableFile) -> Unit): (List<String>) -> Unit {
    val scope = rememberCoroutineScope()
    val opened by rememberUpdatedState(onOpen)
    return remember {
        { types ->
            scope.launch {
                val dialog = FileDialog(null as Frame?, "", FileDialog.LOAD).apply { isVisible = true }
                val directory = dialog.directory ?: return@launch
                val name = dialog.file ?: return@launch
                val content = withContext(Dispatchers.IO) {
                    runCatching { File(directory, name).readText() }
                        .onFailure { Logger.withTag("FileOpener").w(it) { "Couldn't read $name" } }
                        .getOrNull()
                } ?: return@launch
                opened(ShareableFile(name, types.firstOrNull().orEmpty(), content))
            }
        }
    }
}
