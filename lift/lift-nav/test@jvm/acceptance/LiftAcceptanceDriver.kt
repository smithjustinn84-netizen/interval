@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.acceptance

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performTextReplacement
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.bells.ui.BellsAppFixture
import dev.justinsmith.bells.ui.bellsAppTest
import dev.justinsmith.interval.ui.AcceptanceSteps
import dev.justinsmith.interval.ui.JOURNEY_TIMEOUT
import dev.justinsmith.interval.ui.Window
import kotlin.test.assertEquals

/**
 * Lift's acceptance tests in the words of its journeys (the `lift-*` and `exercise-library` files in
 * `journeys/`), over Lift's own pages and fake storage. The rules are the interval side's
 * (`IntervalAcceptanceDriver`): one selector per control, every check waits, outcomes are checked in
 * [BellsAppFixture.storage] too, and time moves only through [BellsAppFixture.clock].
 */
@Suppress("detekt.TooManyFunctions")
class LiftAcceptanceDriver(ui: DesktopComposeUiTest, val app: BellsAppFixture) : AcceptanceSteps(ui) {

    // Today

    fun seeNoSessionsYet() {
        see("No sessions yet")
        see("Bells, barbells, bodyweight, bands and the mat")
    }

    fun startSession() {
        tap("Start session")
        see("Log set")
    }

    fun seeLastSession(vararg lines: String) {
        see("Last session")
        lines.forEach { see(hasText(it, substring = true)) }
    }

    fun openEquipment() {
        tapDescribed("Your equipment and records")
        see("Your bar")
    }

    /** From Today: Follow a program, find [name] on the list, and follow it. */
    fun followProgram(name: String) {
        tap("Follow a program")
        scrollTo(name)
        tap(name)
        tap("Follow this program")
    }

    fun seeProgramDay(day: String, vararg exercises: String) {
        see(day)
        exercises.forEach { see(it) }
    }

    fun startProgramDay(day: Int) {
        tap("Start day $day")
        see("Log set")
    }

    // A session

    /** Logs the set the session's Next set card offers. */
    fun logNextSet() = tap("Log set")

    fun seeSet(set: String) = see(set)

    fun seeMoved(kg: String) = see(kg)

    /** Opens the Log sheet from the title bar, on the exercise the session last had. */
    fun openLogSheet() {
        tapDescribed("Add set or EMOM")
        see("Change")
    }

    /** On the Log sheet: Change, narrow the library by [search] and/or [equipment], then pick [name]. */
    fun pickExercise(name: String, search: String? = null, equipment: String? = null) {
        openLibraryOnSheet()
        search?.let { searchFor(it) }
        equipment?.let { tap(it) }
        tap(name)
        see("Change")
    }

    fun openLibraryOnSheet() {
        tap("Change")
        see("Search exercises")
    }

    /** Types [words] into Search exercises: the library's field, drawn before any sheet's. */
    fun searchFor(words: String) = ui.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement(words)

    fun seeMatches(count: String, vararg names: String) {
        see(count)
        names.forEach { see(it) }
    }

    fun seeRecent(name: String) {
        see("Recent")
        see(name)
    }

    /** Picks a chip on the sheet: a band, a bell, an added weight or a side. */
    fun choose(chip: String) = tap(chip)

    fun seeChosen(chip: String) =
        ui.onAllNodes(hasText(chip) and hasClickAction()).onLast().assertIsSelected()

    fun holdFiveSecondsLonger(times: Int) = repeat(times) { tapDescribed("Five seconds more") }

    fun barHeavier(times: Int) = repeat(times) { tapDescribed("Heavier") }

    /** Logs the set on the Log sheet: its Log set is drawn over the session's own. */
    fun logFromSheet() = tap("Log set")

    /** Changes the block Up next: its Change, then [chip] on the sheet (a bell, say), then Done. */
    fun changeUpNext(chip: String) {
        tap("Change")
        see("How it runs")
        choose(chip)
        tap("Done")
    }

    /** Opens a set already logged, steps its reps up once, and saves it. */
    fun addARepTo(set: String) {
        tap(set)
        see("Save set")
        seeNo("An EMOM")
        tapDescribed("More reps")
        tap("Save set")
    }

    fun openTodaysPlan() = tap("Today's plan")

    /** On Today's plan: skips the block of [exercise]. */
    fun skipBlock(exercise: String) {
        tap(exercise)
        tap("Skip this block")
    }

    fun finish() {
        tap("Finish")
        see("Counts toward your week and your goal")
    }

    fun seeRecord(exercise: String, record: String) {
        see("New records")
        see(exercise)
        see(record)
    }

    fun closeSummary() = tap("Done")

    // The exercise library

    fun openExerciseLibrary() {
        scrollTo("Exercises")
        tap("Exercises")
        see("Search exercises")
    }

    /** From an empty search: the offer to add what was searched for, which opens the exercise's form. */
    fun addSearchedExercise(name: String) {
        see("No exercise matches “$name”")
        tap("Add “$name” as an exercise")
        see("Save exercise")
    }

    fun nameExercise(name: String) =
        ui.onNode(hasSetTextAction() and hasContentDescription("Name")).performTextReplacement(name)

    fun writeCues(cues: String) =
        ui.onNode(hasSetTextAction() and hasContentDescription("Cues")).performTextReplacement(cues)

    fun saveExercise() = tap("Save exercise")

    fun seeCannotSaveExercise() {
        see("One of yours already has this name.")
        ui.onAllNodes(hasText("Save exercise")).onLast().assertIsNotEnabled()
    }

    fun openExercise(name: String) {
        tap(name)
        see("Your history")
    }

    fun customExercises(vararg names: String) =
        assertEquals(names.toList(), app.storage.exercises.rows.value.map { it.name })

    // Outcomes

    fun setsLogged(count: Int) = assertEquals(count, app.storage.sessions.sets.value.size)

    // Navigation

    fun goBack() = tapDescribed("Back")

    fun pressSystemBack() {
        app.back()
        ui.waitForIdle()
    }
}

/** A Lift acceptance test: Lift's pages over [storage], in [window], driven through [LiftAcceptanceDriver]. */
fun liftScenario(
    storage: FakeBellsStorage = FakeBellsStorage(),
    window: Window = Window.Phone,
    scenario: suspend LiftAcceptanceDriver.() -> Unit,
) = bellsAppTest(storage, window = window, timeout = JOURNEY_TIMEOUT) { app ->
    LiftAcceptanceDriver(this, app).scenario()
}
