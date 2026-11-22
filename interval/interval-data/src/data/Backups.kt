package dev.justinsmith.interval.data

import dev.justinsmith.interval.platform.ShareableFile
import dev.justinsmith.interval.platform.Sharer
import dev.justinsmith.interval.platform.WallClock

/**
 * Everything the app stores, as one file to keep or move, which an import reads back. The app builds it, since only
 * the app sees both the workout's storage and Lift's; Settings offers it under Your data, and Today when it is due.
 */
fun interface DataExporter {
    suspend fun export(): ShareableFile
}

/**
 * The log as a spreadsheet: a row for each workout, sport day, Lift session and set, in words, to read rather than
 * to import. The app builds it, as it does [DataExporter]'s file.
 */
fun interface SheetExporter {
    suspend fun export(): ShareableFile
}

/**
 * Exports to keep: the whole log to back up ([backUp], marked as the last backup once it is offered to share or
 * save), the spreadsheet ([shareSheet], no backup), and Today's ask held off a month ([notNow]).
 */
class Backups(
    private val exporter: DataExporter,
    private val sheets: SheetExporter,
    private val sharer: Sharer,
    private val settings: SettingsStore,
    private val clock: WallClock,
) {
    /** Offers the whole log to share or save, and marks it backed up now. */
    suspend fun backUp() {
        sharer.shareFile(exporter.export())
        val now = clock.nowMillis()
        settings.updateAppSettings { it.copy(lastBackupEpochMillis = now) }
    }

    /** Offers the log as a spreadsheet to share or save. */
    suspend fun shareSheet() {
        sharer.shareFile(sheets.export())
    }

    /** Today's ask to back up, put off for another month. */
    fun notNow() {
        val now = clock.nowMillis()
        settings.updateAppSettings { it.copy(backupAskedEpochMillis = now) }
    }
}
