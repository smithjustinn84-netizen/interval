package dev.justinsmith.interval.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The system's document picker. Its answer comes through the activity's result registry, so it reaches the
 * screen as it stands when the picker closes, even an activity recreated behind it; the file is read off the
 * main thread then. No permission is needed: the picker grants this one document.
 */
@Composable
internal actual fun rememberPlatformFileOpener(onOpen: (ShareableFile) -> Unit): (List<String>) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val opened by rememberUpdatedState(onOpen)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val file = withContext(Dispatchers.IO) {
                    runCatching { context.read(uri) }
                        .onFailure { Logger.withTag("FileOpener").w(it) { "Couldn't read the file picked" } }
                        .getOrNull()
                }
                file?.let(opened)
            }
        }
    }
    return remember(launcher) { { types -> launcher.launch(types.toTypedArray()) } }
}

private fun Context.read(uri: Uri): ShareableFile {
    val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
    val content = requireNotNull(contentResolver.openInputStream(uri)) { "No stream for $uri" }.use {
        it.readBytes().decodeToString()
    }
    return ShareableFile(name ?: uri.lastPathSegment.orEmpty(), contentResolver.getType(uri).orEmpty(), content)
}
