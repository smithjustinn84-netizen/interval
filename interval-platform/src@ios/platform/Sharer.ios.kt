@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package dev.justinsmith.interval.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.popoverPresentationController

/**
 * The activity sheet, presented over the key window's root controller or whatever that is showing. A file
 * is written to the temporary directory first, which the system clears; off the main thread, as it is the
 * whole history, and the sheet is then presented on it, as UIKit requires.
 */
internal class IosSharer : Sharer {
    override suspend fun shareFile(file: ShareableFile) {
        val path = NSTemporaryDirectory() + file.name
        withContext(Dispatchers.IO) {
            NSString.create(string = file.content)
                .writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        }
        present(listOf(NSURL.fileURLWithPath(path)))
    }

    override fun shareText(text: String) = present(listOf(text))

    private fun present(items: List<Any>) {
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        val host = root.presentedViewController ?: root
        val sheet = UIActivityViewController(activityItems = items, applicationActivities = null)
        // An iPad shows it as a popover, which needs somewhere to point.
        sheet.popoverPresentationController?.sourceView = host.view
        host.presentViewController(sheet, animated = true, completion = null)
    }
}
