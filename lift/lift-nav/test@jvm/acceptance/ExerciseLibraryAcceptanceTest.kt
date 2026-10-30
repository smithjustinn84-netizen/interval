package dev.justinsmith.lift.acceptance

import androidx.compose.ui.test.hasText
import kotlin.test.Test

/**
 * `journeys/exercise-library.xml`: search and narrow the library, add an exercise from a search that finds
 * nothing, pick it on the Log sheet and log it, then see its equipment locked.
 */
class ExerciseLibraryAcceptanceTest {
    @Test
    fun `an exercise added from an empty search is logged and then keeps its equipment`() = liftScenario {
        openExerciseLibrary()
        seeMatches("94 exercises", "Kettlebell · 19")
        searchFor("PUSHUP")
        seeMatches("3 exercises", "Bodyweight · 3", "Push-up", "Incline push-up", "Pike push-up")
        tap("Clear")
        see("94 exercises")

        choose("Mat")
        seeMatches("18 exercises", "The hundred")
        tap("Filters")
        seeMatches("Focus", "Measured by", "Only yours")
        choose("Time")
        seeMatches("1 exercise", "Swimming", "Filters · 1")
        tap("Clear")
        seeMatches("94 exercises", "Filters")

        searchFor("Copenhagen plank")
        addSearchedExercise("Copenhagen plank")
        choose("Time")
        tap("One side at a time")
        choose("Core")
        writeCues("Top knee on the bench")
        saveExercise()
        seeMatches("1 exercise", "Yours · 1", "Bodyweight · Time a side · Core")
        customExercises("Copenhagen plank")

        openExercise("Copenhagen plank")
        seeMatches("Top knee on the bench", "Not logged yet. Its sets will show here.")
        tap("Edit")
        see("Archive exercise")
        pressSystemBack()
        seeNo("Archive exercise")

        tapDescribed("New exercise")
        nameExercise("copenhagen PLANK")
        seeCannotSaveExercise()
        pressSystemBack()
        seeNo("One of yours already has this name.")
        goBack()

        startSession()
        openLogSheet()
        pickExercise("Copenhagen plank", search = "copen")
        seeMatches("Bodyweight · Time a side · Core", "Top knee on the bench", "0:30")
        logFromSheet()
        seeSet("0:30 · Left")
        setsLogged(1)

        openLogSheet()
        openLibraryOnSheet()
        searchFor("Turkish sit-up")
        addSearchedExercise("Turkish sit-up")
        saveExercise()
        see("Bodyweight · Reps · Full body")
        customExercises("Copenhagen plank", "Turkish sit-up")
        pressSystemBack()
        finish()
        closeSummary()

        openExerciseLibrary()
        searchFor("copenhagen")
        openExercise("Copenhagen plank")
        see(hasText("Done 1 time", substring = true))
        seeMatches("Longest 0:30", "0:30 · Left")
        tap("Edit")
        see("Sets of it are logged, so its equipment and measure stay as they are.")
        pressSystemBack()

        searchFor("")
        scrollTo("Goblet squat")
        openExercise("Goblet squat")
        seeMatches("Kettlebell · Reps · Lower body", "Not logged yet. Its sets will show here.")
        seeNo("Edit")
    }
}
