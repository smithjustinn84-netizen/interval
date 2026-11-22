@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.acceptance

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import dev.justinsmith.interval.ui.AcceptanceSteps
import dev.justinsmith.interval.ui.JOURNEY_TIMEOUT
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.program
import dev.justinsmith.lift.fakes.FakeIntervalActivity
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.ui.LiftAppFixture
import dev.justinsmith.lift.ui.liftAppTest
import kotlin.test.assertEquals

/**
 * Lift's acceptance tests in the words of its journeys (the `lift-*` and `exercise-library` files in
 * `journeys/`), over Lift's own pages and fake storage. The rules are the interval side's
 * (`IntervalAcceptanceDriver`): one selector per control, every check waits, outcomes are checked in
 * [LiftAppFixture.storage] too, and time moves only through [LiftAppFixture.clock].
 */
class LiftAcceptanceDriver(ui: DesktopComposeUiTest, val app: LiftAppFixture) : AcceptanceSteps(ui) {

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

    /** The balance waits for two sessions: with Today's last card in view, there is none. */
    fun seeNoBalanceYet() {
        scrollTo("Exercises")
        seeNo("Balance")
    }

    /** Today's balance, each of [rows] a group's line as a screen reader says it ("Back: 2 sets worked it most"). */
    fun seeBalance(vararg rows: String) {
        scrollTo("Balance")
        rows.forEach { see(hasContentDescription(it, substring = true)) }
    }

    fun openEquipment() {
        scrollTo("Your equipment")
        tap("Your equipment")
        see("Your bar")
    }

    /** From Today: Follow a program, find [name] on the list, and follow it. */
    fun followProgram(name: String) {
        tap("Follow a program")
        scrollTo(name)
        tap(name)
        tap("Follow this program")
    }

    /** From Today: Follow a program, find [name] on the list, and open its sheet. */
    fun openProgram(name: String) {
        tap("Follow a program")
        scrollTo(name)
        tap(name)
        see("Day 1")
    }

    /** About how long a day takes, as a program's sheet says of each of its days and Today of the day followed. */
    fun seeEstimate(words: String) = see(words)

    /** From a program's sheet: follow it, which closes Programs. */
    fun followFromSheet() {
        tap("Follow this program")
        seeNo("Follow this program")
    }

    /**
     * The sheet's balance of a week of the program: each of [rows] a group's line as a screen reader says it, and
     * the line naming what it leaves [untrained].
     */
    fun seeWeekBalance(untrained: String, vararg rows: String) {
        see("Sets in a week")
        rows.forEach { see(hasContentDescription(it, substring = true)) }
        see("Nothing in a week for: $untrained")
    }

    /** From Today, following a program: All programs, find [name] on the list, and open its sheet. */
    fun openProgramToChange(name: String) {
        scrollTo("All programs")
        tap("All programs")
        scrollTo(name)
        tap(name)
        see("Day 1")
    }

    /** From a built-in's sheet: open a copy of it in the editor, named [name]. */
    fun makeItYours(name: String) {
        tap("Make it yours")
        see("New program")
        see(hasText(name) and hasSetTextAction())
    }

    /** In the program editor: a week more, by its stepper. */
    fun aWeekMore() = tapDescribed("More: Weeks")

    /** Saves the program in the editor, which closes. */
    fun saveProgram() {
        tap("Save program")
        seeNo("Save program")
    }

    /** The program followed, as the store has it: one the user made, named [name], of [weeks]. */
    fun followingMine(name: String, weeks: Int) = waitFor("to follow \"$name\" of $weeks weeks") {
        val id = app.storage.following.row.value?.programId
        app.storage.programs.rows.value.any { "custom:${it.uid}" == id && it.name == name && it.weeks == weeks }
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

    /** From Today, with a program followed: the free session under On your own. */
    fun startFreeSession() {
        scrollTo("Free session")
        tap("Free session")
        see("Log set")
    }

    /** Logs the set the session's Next set card offers. */
    fun logNextSet() = tap("Log set")

    // A program's day in a free session

    /** Opens the programs under the Next set card, to take a day of one; the sheet's title is only read aloud. */
    fun doAProgramDay() {
        tap("Do a program day")
        see("Built-in")
    }

    /** Opens the program [name] on the sheet, on its days. */
    fun pickProgram(name: String) {
        tap(name)
        see("All programs")
    }

    fun seeDays(vararg days: String) = days.forEach { see(it) }

    /** [day] on the sheet marked as the followed program's next. */
    fun seeDayNext(day: String) {
        see(day)
        see("Next")
    }

    fun pickDay(day: String) {
        tap(day)
        see("Today's plan")
    }

    fun seeUpNext(progress: String) = see("Up next · $progress")

    /** A figure doing [exercise] on the card about the next set. */
    fun seeNextLiftShown(exercise: String) = see(hasContentDescription("A figure doing $exercise"))

    /** The rest after a set counting down, [left] on it. */
    fun seeResting(left: String) {
        see("Rest")
        see(left)
    }

    /** On the rest's band, under its clock: the set the rest leads to, [set] ("Two-hand swing · 16 kg × 10"). */
    fun seeRestLeadsTo(set: String) = see("Next: $set")

    fun skipRest() = tap("Skip")

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

    /** On the Log sheet: switches Warm-up, on or off. */
    fun markWarmUp() = tap(hasText("Warm-up") and isToggleable())

    fun seeWarmUp(on: Boolean) = see(hasText("Warm-up") and isToggleable() and if (on) isOn() else isOff())

    /** Opens the logged set [line] on the sheet, to change it. */
    fun editSet(line: String) {
        tap(line)
        see("Save set")
    }

    fun saveSet() = tap("Save set")

    /** On the card about the next set, under its figure: the worst fault the lift is prone to. */
    fun seeWatchFor(fault: String) = see("Watch for · $fault")

    /** On the Log sheet: An EMOM, then Start EMOM on its draft's lift, which counts in. */
    fun startEmom() {
        tap("An EMOM")
        tap("Start EMOM")
        see("Get ready")
    }

    /** In an EMOM's minute, once its Work shows: Set done, and it rests out the minute. */
    fun emomSetDone() {
        see("Work")
        tap("Set done")
        see("Rest")
    }

    /**
     * From Today: a new program named [name] of one day, its one block the editor's first (two-hand swings, 5 × 10)
     * made rounds in a time cap, which opens at its least, five minutes; then follow it.
     */
    fun followNewDensityProgram(name: String) {
        tap("Follow a program")
        scrollTo("New program")
        tap("New program")
        see("Day 1")
        tap("Add block")
        tap("Time cap")
        tap("Done")
        see("As many rounds as you can in 5 min · 10 a round")
        ui.onNode(hasSetTextAction()).performTextReplacement(name)
        saveProgram()
        tap(name)
        tap("Follow this program")
    }

    /** In a program day with a density block up next: Start the clock, which counts in, then the cap's Go. */
    fun startTheClock() {
        tap("Start the clock")
        see("Get ready")
        secondsPass(10)
        see("Go")
    }

    /** On an EMOM's or a density block's page: the ghost doing [exercise], the block's lift, by its timer. */
    fun seeBlockLiftShown(exercise: String) = see(hasContentDescription("A figure doing $exercise"))

    /** In a density block's cap: Round done [times], the rounds counting up to [total]. */
    fun roundsDone(times: Int, total: Int) {
        repeat(times) { tap("Round done") }
        see("$total rounds")
    }

    // Tests

    /** From Today: the tests under On your own, each with its last result or that it hasn't been done. */
    fun openTests() {
        scrollTo("Tests")
        tap("Tests")
        see("Snatch test")
    }

    /** On Tests: opens the sheet of the test [name], which names it again over how it is done and Start. */
    fun openTest(name: String) {
        tap(name)
        see("Start the test")
    }

    /** On a test's sheet: starts it, which opens its session with the test's one block up next. */
    fun startTheTest() {
        tap("Start the test")
        see("Finish")
    }

    /** After a test's clock, part way through a round: its [reps] stepped up and added to the count. */
    fun addPartRound(reps: Int) {
        see("Reps in the round you were on when time was called")
        repeat(reps) { tapDescribed("More reps") }
        tap("Add to the count")
        seeNo("Add to the count")
    }

    /** [seconds] pass on the clocks the timers keep. */
    fun secondsPass(seconds: Int) = app.clock.set(app.clock.get() + seconds * MILLIS_PER_SECOND)

    /** The last line the session said aloud is [line], in the lift's own words. */
    fun hear(line: String) = waitFor("to hear \"$line\" last") {
        app.speaker.lines.value.lastOrNull() == line
    }

    /** Nothing has been said aloud at all. */
    fun heardNothing() = assertEquals(emptyList(), app.speaker.lines.value)

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

    // A program's last day and its recap

    /** From Strength: starts [id]'s last day, logs each of its sets as planned, and finishes. */
    fun doTheLastDay(id: ProgramId) {
        val program = program(id)
        startProgramDay(program.totalDays)
        repeat(program.day(program.totalDays).plannedSets().size) { logNextSet() }
        tap("Finish")
    }

    /**
     * The summary's See the recap; the step up it offers is dropped when asked about first, as leaving asks of
     * changes not yet kept.
     */
    fun seeTheRecap() {
        tap("See the recap")
        waitFor("the recap, or the changes asked about") {
            ui.onAllNodes(hasText("Next cycle")).fetchSemanticsNodes().isNotEmpty() ||
                ui.onAllNodes(hasText("Drop")).fetchSemanticsNodes().isNotEmpty()
        }
        if (ui.onAllNodes(hasText("Drop")).fetchSemanticsNodes().isNotEmpty()) tap("Drop")
        see("Next cycle")
    }

    fun finish() {
        tap("Finish")
        see("Close")
    }

    /** Starts day 1 and logs its [sets] sets, then Finish. */
    fun doTheDay(sets: Int) {
        startProgramDay(1)
        repeat(sets) { logNextSet() }
        tap("Finish")
    }

    /** Next time on the summary offers [lift] stepped as [line], and says [why]. */
    fun seeNextTime(lift: String, line: String, why: String) {
        see("Next time")
        see(lift)
        see(line)
        see(why)
    }

    fun seeRecord(exercise: String, record: String) {
        see("New records")
        see(exercise)
        see(record)
    }

    fun closeSummary() = tap("Close")

    /** Before the first set of a taxed day: the easier day offered, and why. */
    fun seeEasierDay(why: String) {
        see("Easier today")
        see(why)
    }

    /** Takes the easier day: the offer goes, and today's plan is kept easier, its first block of [rounds]. */
    fun useEasierDay(rounds: Int) {
        tap("Use it")
        seeNo("Easier today")
        waitFor("today's plan kept with $rounds rounds") {
            app.storage.sessions.sessions.value
                .any { it.finishedAtEpochMillis == null && it.plan?.contains("\"rounds\":$rounds") == true }
        }
    }

    /** On the summary, under "How was it?": how hard the session felt, then a note on it. */
    fun sayHowItWas(effort: String, note: String) {
        see("How was it?")
        tap(hasText(effort) and isSelectable())
        ui.onNode(hasSetTextAction()).performScrollTo().performTextInput(note)
    }

    /** The finished session is kept with how it felt: an effort of [level], 1 to 5, and its [note]. */
    fun sessionKeptAs(level: Int, note: String) = waitFor("the session kept as $level, \"$note\"") {
        app.storage.sessions.sessions.value.any { it.effort == level && it.note == note }
    }

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

    /** Opens the editor's figures and picks the built-in [exercise] to show it by, which then stands in the row. */
    fun chooseFigure(exercise: String) {
        tap(hasText("Change") and hasClickAction())
        tap(hasText(exercise) and isSelectable())
        seeNo(hasText("No figure") and isSelectable())
        see(exercise)
    }

    fun seeCannotSaveExercise() {
        see("One of yours already has this name.")
        ui.onAllNodes(hasText("Save exercise")).onLast().assertIsNotEnabled()
    }

    fun openExercise(name: String) {
        tap(name)
        see("Your history")
    }

    /** A figure doing [exercise] moves on its page. */
    fun seeDemonstration(exercise: String) = see(hasContentDescription("$exercise, shown by a figure"))

    fun seeNoDemonstration() = seeNo(hasContentDescription("shown by a figure", substring = true))

    /**
     * [exercise]'s page draws it from the side with its bell's path, under what that part of the rep is called and
     * the [cue] for it.
     */
    fun seeBellPath(exercise: String, phase: String, cue: String) {
        see(hasContentDescription("$exercise from the side, with the path its bell takes"))
        see(phase)
        see(cue)
    }

    fun seeNoBellPath() = seeNo(hasContentDescription("from the side, with the path its bell takes", substring = true))

    /**
     * Touching the bar under the bell's path [at] that share of the way along holds the rep in [phase], with its
     * [cue], and offers to play it on.
     */
    fun scrubBellPath(at: Float, phase: String, cue: String) {
        ui.onNode(hasContentDescription("Where in the bell's path"))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(at) }
        see(phase)
        see(cue)
        see(hasContentDescription("Play the bell path"))
    }

    /** Play sets the held rep running again. */
    fun playBellPath() {
        tapDescribed("Play the bell path")
        see(hasContentDescription("Pause the bell path"))
        seeNo(hasContentDescription("Play the bell path"))
    }

    /** Pause holds the rep where it is. */
    fun pauseBellPath() {
        tapDescribed("Pause the bell path")
        see(hasContentDescription("Play the bell path"))
    }

    /** The bell's path runs at half speed, for a form check, and at its own again. */
    fun slowBellPath() {
        tap("1×")
        see("0.5×")
    }

    fun fullSpeedBellPath() {
        tap("0.5×")
        see("1×")
    }

    /**
     * Step holds the bell's path where the next part of the rep starts: the tag names it ([to]) and the one of the
     * lift's written cues it belongs to ([cue]) is lit.
     */
    fun stepBellPath(to: String, cue: String) {
        tap("Step")
        see(to)
        seeCurrentCue(cue)
        see(hasContentDescription("Play the bell path"))
    }

    /** Taps one of the lift's written cues under its bell's path, which holds the rep where its [part] starts. */
    fun showPathCue(cue: String, part: String) {
        tap(cue)
        see(part)
        seeCurrentCue(cue)
        see(hasContentDescription("Play the bell path"))
    }

    fun pauseDemonstration() {
        tapDescribed("Pause")
        see(hasContentDescription("Play"))
    }

    /** Taps a cue under the figure, which stops it where the cue starts and lights that cue. */
    fun showCuePart(cue: String) {
        tap(cue)
        see(hasContentDescription("Play"))
        seeCurrentCue(cue)
    }

    fun seeCurrentCue(cue: String) = see(hasText(cue) and isSelected())

    /** The figure's cues grouped: what is done once before the reps, then what each rep does. */
    fun seeSetUpThenEachRep() = seeMatches("Set up", "Each rep")

    /** Slows the figure to half speed, which 0.5× puts back. */
    fun slowDemonstration() {
        tap("1×")
        see("0.5×")
    }

    /** Steps the figure on to where the next of its cues starts, which stops it there and lights [to]. */
    fun stepDemonstration(to: String) {
        tap("Step")
        see(hasContentDescription("Play"))
        seeCurrentCue(to)
    }

    /**
     * Under Watch for, taps a fault the figure can show (by its [correction]): the figure does it wrong, marked so,
     * and the row is lit.
     */
    fun showFault(correction: String) {
        see("Watch for")
        tap(correction)
        see(hasContentDescription("Done wrong", substring = true))
        see(hasText(correction) and isSelected())
    }

    /** A fault only said in words: the figure can't show it, so its row offers nothing to tap. */
    fun seeFaultNotShown(correction: String) = see(hasText(correction) and !hasClickAction())

    /** Taps the fault shown again: the figure does it right. */
    fun hideFault(correction: String) {
        tap(correction)
        seeNo(hasContentDescription("Done wrong", substring = true))
    }

    /** Taps the set-up's cue, which plays the set-up again and lights it. */
    fun showSetUp(cue: String) {
        tap(cue)
        see(hasContentDescription("Pause"))
        seeCurrentCue(cue)
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

private const val MILLIS_PER_SECOND = 1_000L

/** A Lift acceptance test: Lift's pages over [storage], in [window], driven through [LiftAcceptanceDriver]. */
fun liftScenario(
    storage: FakeLiftStorage = FakeLiftStorage(),
    window: Window = Window.Phone,
    intervals: FakeIntervalActivity = FakeIntervalActivity(),
    scenario: suspend LiftAcceptanceDriver.() -> Unit,
) = liftAppTest(storage, intervals, window = window, timeout = JOURNEY_TIMEOUT) { app ->
    LiftAcceptanceDriver(this, app).scenario()
}
