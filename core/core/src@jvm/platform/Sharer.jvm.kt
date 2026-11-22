package dev.justinsmith.interval.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

/** A desktop has no share sheet: a file or a picture goes to a save dialog, text to the clipboard. */
internal class DesktopSharer : Sharer {
    override suspend fun shareFile(file: ShareableFile) = save(file.name) { it.writeText(file.content) }

    override suspend fun shareImage(name: String, png: ByteArray) = save(name) { it.writeBytes(png) }

    private suspend fun save(suggested: String, write: (File) -> Unit) {
        val dialog = FileDialog(null as Frame?, suggested, FileDialog.SAVE).apply {
            this.file = suggested
            isVisible = true
        }
        val directory = dialog.directory ?: return
        val name = dialog.file ?: return
        withContext(Dispatchers.IO) { write(File(directory, name)) }
    }

    override fun shareText(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}
