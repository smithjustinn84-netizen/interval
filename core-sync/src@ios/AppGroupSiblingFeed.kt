@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package dev.justinsmith.core.sync

import co.touchlab.kermit.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * Both apps' snapshots sit side by side in the App Group container [groupId], one file each
 * (`feeds/interval.json`, `feeds/bells.json`), each written only by its own app. Without the App Group
 * entitlement, which needs both apps signed by one team, there is no container and the feed does nothing.
 */
class AppGroupSiblingFeed(private val self: AppId, private val groupId: String = APP_GROUP) : SiblingFeed {
    private val log = Logger.withTag("SiblingFeed")

    private val directory: String? by lazy {
        val container = NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(groupId)?.path
        container?.let { "$it/feeds" }?.also {
            NSFileManager.defaultManager.createDirectoryAtPath(
                it,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
    }

    override fun publish(text: String) {
        val dir = directory ?: return
        val written = NSString.create(string = text).writeToFile(
            "$dir/${fileName(self)}",
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
        if (!written) log.w { "Couldn't publish the feed" }
    }

    override fun readSibling(): String? {
        val dir = directory ?: return null
        return NSString.stringWithContentsOfFile("$dir/${fileName(self.sibling)}", NSUTF8StringEncoding, null)
    }

    private fun fileName(app: AppId) = "${app.name.lowercase()}.json"

    companion object {
        /** The App Group both apps list in their entitlements. */
        const val APP_GROUP = "group.dev.justinsmith"
    }
}
