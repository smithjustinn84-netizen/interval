package dev.justinsmith.interval.export

import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.platform.ShareableFile
import dev.justinsmith.interval.settings.DataImporter
import dev.justinsmith.interval.settings.ImportCounts
import dev.justinsmith.interval.settings.ImportReview
import dev.justinsmith.lift.data.LiftRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads an export file back into both repositories: [review] plans it against what is stored and writes nothing;
 * [import] plans it again, against what is stored by then, and writes that plan, read and planned on
 * [computation]. Each side writes in one transaction; the two are separate databases, so should the second
 * fail after the first landed, importing the file again adds what is missing and nothing twice.
 */
class AppDataImporter(
    private val intervals: IntervalRepository,
    private val lift: LiftRepository,
    private val computation: CoroutineDispatcher = Dispatchers.Default,
) : DataImporter {
    override suspend fun review(file: ShareableFile): ImportReview = when (val reading = read(file)) {
        is ImportReading.Ready -> ImportReview.Ready(
            reading.plan.exportedAtEpochMillis,
            reading.plan.version,
            reading.plan.added,
            reading.plan.alreadyHere,
        )

        is ImportReading.TooNew -> ImportReview.TooNew(reading.version)

        ImportReading.Unreadable -> ImportReview.Unreadable
    }

    override suspend fun import(file: ShareableFile, withSettings: Boolean): ImportCounts {
        val plan =
            (read(file) as? ImportReading.Ready)?.plan ?: throw IllegalArgumentException("Not an export to import")
        intervals.importAll(plan.history, plan.presets, plan.planned)
        val liftImport = if (withSettings) plan.lift.copy(restSeconds = plan.settings.restSeconds) else plan.lift
        lift.importAll(liftImport)
        if (withSettings) {
            intervals.settings.updateAppSettings { plan.settings.app }
            intervals.settings.updateCueSettings { plan.settings.cues }
            intervals.settings.updateConfig { plan.settings.timer }
        }
        return plan.added
    }

    private suspend fun read(file: ShareableFile): ImportReading {
        val holdings = holdings()
        return withContext(computation) { readImport(file.content, holdings) }
    }

    /** What both sides hold right now, taken on the caller's thread as the repositories' flows stand. */
    private fun holdings() = Holdings(
        history = intervals.history.history.value,
        presets = intervals.presets.presets.value,
        planned = intervals.plans.planned.value,
        sessions = lift.sessions.sessions.value,
        exercises = lift.exercises.customExercises.value,
        programs = lift.programs.customPrograms.value,
        bands = lift.gear.bands.value,
        bellsKg = lift.gear.bells.value,
        barKg = lift.gear.bar.value,
        followed = lift.programs.followed.value,
    )
}
