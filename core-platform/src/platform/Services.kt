package dev.justinsmith.interval.platform

/** What the installed build says about itself, for the About screen. */
interface AppInfo {
    /**
     * The version the store shows, such as "0.1.0", read from the package or bundle; null when the
     * build carries none, as a desktop run from source does.
     */
    val version: String?
}

/** A text file the app offers the system: its [name] with its extension, its [mimeType], and its [content]. */
data class ShareableFile(val name: String, val mimeType: String, val content: String)

/**
 * Hands something of the user's to the system: Android's share sheet, iOS's activity sheet; on desktop a file
 * goes to a save dialog and text to the clipboard. Injected, so tests record what was offered.
 */
interface Sharer {
    /** Offers [file] to share or save. It is written somewhere temporary first, so this may suspend. */
    suspend fun shareFile(file: ShareableFile)

    /** Offers [text] to share. */
    fun shareText(text: String)
}
