package dev.justinsmith.core.sync

/**
 * Where this app leaves its [FeedSnapshot] and finds its sibling's. Implementations never throw: a sibling
 * that isn't installed, a missing permission or entitlement, or an unreadable file all read as null.
 * They block on file I/O, so call them off the main thread.
 */
interface SiblingFeed {
    /** Replaces this app's published snapshot with [text], all at once. */
    fun publish(text: String)

    /** The sibling's published snapshot, as text, or null when there is none to read. */
    fun readSibling(): String?
}

/** No sibling to share with: desktop, tests. */
object NoSiblingFeed : SiblingFeed {
    override fun publish(text: String) = Unit

    override fun readSibling(): String? = null
}
