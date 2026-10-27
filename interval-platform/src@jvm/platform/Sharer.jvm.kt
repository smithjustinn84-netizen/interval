package dev.justinsmith.interval.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

/** A desktop has no share sheet: a file goes to a save dialog, text to the clipboard. */
internal class DesktopSharer : Sharer {
    override suspend fun shareFile(file: ShareableFile) {
        val dialog = FileDialog(null as Frame?, file.name, FileDialog.SAVE).apply {
            this.file = file.name
            isVisible = true
        }
        val directory = dialog.directory ?: return
        val name = dialog.file ?: return
        withContext(Dispatchers.IO) { File(directory, name).writeText(file.content) }
    }

    override fun shareText(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}
