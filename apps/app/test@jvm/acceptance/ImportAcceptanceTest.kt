package dev.justinsmith.interval.acceptance

import dev.justinsmith.interval.fixtures.DAMAGED_EXPORT
import dev.justinsmith.interval.fixtures.SCHEMA_1_EXPORT
import dev.justinsmith.interval.fixtures.SCHEMA_7_EXPORT
import kotlin.test.Test

/** `journeys/settings.xml`'s Import: a file another phone exported comes in once, and a bad one changes nothing. */
class ImportAcceptanceTest {
    @Test
    fun `an export from another phone comes in once`() = acceptanceScenario {
        openSettings()
        importFile(SCHEMA_1_EXPORT)
        seeImportReview(
            exported = "by Interval 0.2.0",
            new = "New: 2 workouts, 1 Lift session, 1 preset, 1 plan",
        )
        confirmImport()
        seeImported("Imported 2 workouts, 1 Lift session, 1 preset, 1 plan")
        savedWorkouts(2)
        savedPresetsNamed("Hills")

        importFile(SCHEMA_1_EXPORT)
        see("Everything in it is already here.")
        seeAlreadyHere(5)
        cancelImport()
        savedWorkouts(2)
    }

    @Test
    fun `a damaged file or one from a newer app changes nothing`() = acceptanceScenario {
        openSettings()
        importFile(DAMAGED_EXPORT)
        seeImportRefused("isn't an Interval export")
        importFile(SCHEMA_7_EXPORT)
        seeImportRefused("from a newer Interval, 0.9.0")
        nothingIsSaved()
    }
}
