package dev.justinsmith.interval.settings

import dev.justinsmith.interval.platform.ShareableFile

/**
 * Reads an export file back in: [review] weighs it against what is stored and writes nothing, [import] adds
 * what is new. The app does both, since only it sees the workout's storage and Lift's; Settings offers it
 * under Your data, after [Export][DataExporter].
 */
interface DataImporter {
    /** What importing [file] would add, or why it can't be imported. */
    suspend fun review(file: ShareableFile): ImportReview

    /**
     * Adds what [file] holds that is not here yet, its settings too when [withSettings], and returns what was
     * added. Nothing stored is changed or removed. Throws when the file can't be imported or a write fails.
     */
    suspend fun import(file: ShareableFile, withSettings: Boolean): ImportCounts
}

/** What a file comes to before it is imported. */
sealed interface ImportReview {
    /** An export of this app's, written at [exportedAtEpochMillis] by [version] (null when it didn't say). */
    data class Ready(
        val exportedAtEpochMillis: Long,
        val version: String?,
        /** What is not here yet, kind by kind. */
        val new: ImportCounts,
        /** What the file holds that is already here, of every kind together. */
        val alreadyHere: Int,
    ) : ImportReview

    /** An export from a newer build than this one, [version] when the file names it: update the app first. */
    data class TooNew(val version: String?) : ImportReview

    /** Not an export of this app's, or one that is damaged. */
    data object Unreadable : ImportReview
}

/** Things an import adds, kind by kind. */
data class ImportCounts(
    val workouts: Int = 0,
    val presets: Int = 0,
    val plans: Int = 0,
    val sessions: Int = 0,
    val exercises: Int = 0,
    val programs: Int = 0,
    val flows: Int = 0,
) {
    val total: Int get() = workouts + presets + plans + sessions + exercises + programs + flows
}
