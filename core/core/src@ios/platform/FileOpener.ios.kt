@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.darwin.NSObject

/**
 * The document picker, presented over the key window's root controller or whatever that is showing, as the
 * share sheet is. It hands over a copy of the file, which is read off the main thread.
 */
@Composable
internal actual fun rememberPlatformFileOpener(onOpen: (ShareableFile) -> Unit): (List<String>) -> Unit {
    val scope = rememberCoroutineScope()
    val opened by rememberUpdatedState(onOpen)
    // Held here: the picker keeps only a weak reference to its delegate.
    val delegate = remember {
        PickerDelegate { url ->
            scope.launch {
                val content = withContext(Dispatchers.IO) {
                    NSString.stringWithContentsOfURL(url, encoding = NSUTF8StringEncoding, error = null)
                } ?: return@launch
                opened(ShareableFile(url.lastPathComponent.orEmpty(), "", content))
            }
        }
    }
    return remember(delegate) {
        { types ->
            val root = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (root != null) {
                val contentTypes = types.mapNotNull { UTType.typeWithMIMEType(it) }.ifEmpty { listOf(UTTypeJSON) }
                val picker = UIDocumentPickerViewController(forOpeningContentTypes = contentTypes, asCopy = true)
                picker.delegate = delegate
                (root.presentedViewController ?: root).presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}

private class PickerDelegate(private val onPicked: (NSURL) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        (didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(onPicked)
    }
}
