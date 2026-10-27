package dev.justinsmith.interval.platform

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The share sheet. A file is written under the cache directory's `exports/` and offered through the app's
 * `FileProvider` (declared in androidApp's manifest, its paths in `res/xml/file_paths.xml`): the app picked
 * reads it through the provider, and the cache is the system's to clear.
 */
internal class AndroidSharer(private val context: Context) : Sharer {
    override suspend fun shareFile(file: ShareableFile) {
        val uri = withContext(Dispatchers.IO) {
            val target = File(File(context.cacheDir, EXPORTS_DIR).apply { mkdirs() }, file.name)
            target.writeText(file.content)
            FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, target)
        }
        open(
            Intent(Intent.ACTION_SEND).apply {
                type = file.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
    }

    override fun shareText(text: String) {
        open(
            Intent(Intent.ACTION_SEND).apply {
                type = TEXT_MIME_TYPE
                putExtra(Intent.EXTRA_TEXT, text)
            },
        )
    }

    /** From the application context, so the chooser needs a task of its own. */
    private fun open(send: Intent) {
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private companion object {
        const val EXPORTS_DIR = "exports"
        const val AUTHORITY_SUFFIX = ".files"
        const val TEXT_MIME_TYPE = "text/plain"
    }
}
