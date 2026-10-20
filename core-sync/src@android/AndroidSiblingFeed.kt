package dev.justinsmith.core.sync

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import co.touchlab.kermit.Logger
import java.io.File
import java.io.FileNotFoundException

/**
 * This app's snapshot is a file in its own storage, served read-only to its sibling by [FeedProvider];
 * the sibling's is read through the sibling's provider. Android lets the read through only when both
 * apps hold the signature permission `dev.justinsmith.permission.SYNC`, so only apps signed with the
 * same key can read each other.
 */
class AndroidSiblingFeed(context: Context, private val self: AppId) : SiblingFeed {
    private val context = context.applicationContext
    private val log = Logger.withTag("SiblingFeed")

    override fun publish(text: String) {
        runCatching {
            val file = feedFile(context)
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "$FEED_FILE.tmp")
            temp.writeText(text)
            check(temp.renameTo(file)) { "Couldn't replace $file" }
        }.onFailure { log.w(it) { "Couldn't publish the feed" } }
    }

    // The sibling not being installed (unknown authority), not holding the permission yet, or having
    // published nothing all land here as null.
    override fun readSibling(): String? = runCatching {
        context.contentResolver.openInputStream(feedUri(self.sibling))?.use { it.readBytes().decodeToString() }
    }.getOrNull()

    companion object {
        /** Where an app's provider serves its snapshot: `content://<package>.feed/feed`. */
        fun feedUri(app: AppId): Uri = Uri.parse("content://${app.packageId}.feed/feed")

        internal fun feedFile(context: Context) = File(File(context.filesDir, "feeds"), FEED_FILE)
    }
}

/**
 * Serves this app's snapshot file and nothing else. It reads no database and needs no app container,
 * so it works in a process started just to answer it. Each app declares it in its manifest with the
 * authority `<package>.feed`, exported behind `dev.justinsmith.permission.SYNC`.
 */
class FeedProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("The feed is read-only")
        val file = AndroidSiblingFeed.feedFile(requireNotNull(context))
        if (!file.exists()) throw FileNotFoundException("Nothing published yet")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "application/json"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}

/** The published file's name, inside `files/feeds/`. */
private const val FEED_FILE = "feed.json"
