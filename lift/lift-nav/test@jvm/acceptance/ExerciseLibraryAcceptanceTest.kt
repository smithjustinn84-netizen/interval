package dev.justinsmith.lift.acceptance

import androidx.compose.ui.test.hasText
import kotlin.test.Test

/**
 * `journeys/exercise-library.xml`: search and narrow the library, add an exercise from a search that finds
 * nothing, pick it on the Log sheet and log it, then see its equipment locked, and a built-in shown by a figure;
 * one of the user's own shown by a built-in's figure.
 */
class ExerciseLibraryAcceptanceTest {
    @Test
    fun `an exercise added from an empty search is logged and then keeps its equipment`() = liftScenario {
        openExerciseLibrary()
        seeMatches("134 exercises", "Kettlebell · 30")
        searchFor("PUSHUP")
        seeMatches("3 exercises", "Bodyweight · 3", "Push-up", "Incline push-up", "Pike push-up")
        tap("Clear")
        see("134 exercises")

        choose("Mat")
        seeMatches("47 exercises", "The hundred")
        tap("Filters")
        seeMatches("Focus", "Measured by", "Only yours")
        choose("Time")
        seeMatches("22 exercises", "Swimming", "Cat-cow", "Filters · 1")
        tap("Clear")
        seeMatches("134 exercises", "Filters")

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
        seeNoDemonstration()
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
        seeNoBellPath()
        tap("Edit")
        see("Sets of it are logged, so its equipment and measure stay as they are.")
        pressSystemBack()

        searchFor("")
        scrollTo("Goblet squat")
        openExercise("Goblet squat")
        seeMatches("Kettlebell · Reps · Lower body", "Not logged yet. Its sets will show here.")
        seeNo("Edit")
        seeDemonstration("Goblet squat")
        seeBellPath("Goblet squat", phase = "Bottom")
        pauseDemonstration()
        showCuePart("Push the knees out and drive up through the whole foot")
        showCuePart("Sit between your heels with the chest up")
        seeSetUpThenEachRep()
        showSetUp("Hold the bell by the horns at your chest, elbows down")
    }

    @Test
    fun `a swing's figure is slowed and stepped through and shown done wrong`() = liftScenario {
        openExerciseLibrary()
        scrollTo("Two-hand swing")
        openExercise("Two-hand swing")
        seeBellPath("Two-hand swing", phase = "Float")
        scrubBellPath(at = 0.9f, phase = "Hike pass")
        scrubBellPath(at = 0.05f, phase = "Hip snap")
        resumeBellPath()
        slowDemonstration()
        stepDemonstration(to = "Let it float to chest height, then wait until your upper arms touch your ribs to hinge")
        stepDemonstration(to = "Set the bell a foot in front, hinge at the hips and hike it high between your legs")
        showFault("Hips back, shins stay vertical")
        seeFaultNotShown("Loosen at the top, grip on the way down")
        hideFault("Hips back, shins stay vertical")
    }

    @Test
    fun `an exercise of the user's own is shown by a built-in's figure`() = liftScenario {
        openExerciseLibrary()
        searchFor("Pause goblet squat")
        addSearchedExercise("Pause goblet squat")
        see("No figure")
        choose("Kettlebell")
        writeCues("Two seconds at the bottom")
        chooseFigure("Goblet squat")
        choose("Bodyweight")
        see("No figure")
        choose("Kettlebell")
        chooseFigure("Goblet squat")
        saveExercise()

        openExercise("Pause goblet squat")
        seeDemonstration("Pause goblet squat")
        seeBellPath("Pause goblet squat", phase = "Bottom")
        see("Two seconds at the bottom")
        seeNo("Watch for")
        tap("Edit")
        see("Goblet squat")
        pressSystemBack()
    }
}
