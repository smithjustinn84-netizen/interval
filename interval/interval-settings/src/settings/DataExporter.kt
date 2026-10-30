package dev.justinsmith.interval.settings

import dev.justinsmith.interval.platform.ShareableFile

/**
 * Everything the app stores, as one file to keep or move. The app builds it, since only the app sees both
 * the workout's storage and Lift's; Settings offers it under Your data.
 */
fun interface DataExporter {
    suspend fun export(): ShareableFile
}
