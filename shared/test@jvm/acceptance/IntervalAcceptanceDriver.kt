@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.acceptance

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import dev.justinsmith.interval.AppFixture
import dev.justinsmith.interval.appTest
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.tab
import dev.justinsmith.interval.ui.AcceptanceSteps
import dev.justinsmith.interval.ui.Window
import kotlin.test.assertEquals
import kotlin.time.Duration

/**
 * The interval side's acceptance tests in the words of its journeys (the files in `journeys/`): each operation is
 * something a person does or sees, and the English it matches lives here, not in the specs.
 *
 * The rules: one selector per control, every check waits for the state to arrive (the view models emit on
 * Swing's thread), outcomes are checked in the repository as well as on screen, and time moves only through
 * [AppFixture.advance].
 */
@Suppress("detekt.TooManyFunctions")
class IntervalAcceptanceDriver(ui: DesktopComposeUiTest, val app: AppFixture) : AcceptanceSteps(ui) {

    // Navigation

    fun openTab(name: String) = ui.tab(name).performClick()

    fun pressSystemBack() {
        app.back()
        ui.waitForIdle()
    }

    fun goBack() = tapDescribed("Back")

    fun seeTimer() {
        see("Start workout")
        ui.tab("Timer").assertIsSelected()
    }

    // The Timer

    fun setWorkTime(value: String) {
        tap("Work")
        see("Quick picks")
        tap(value)
        tap("Done")
        see(hasText("Work") and hasText(value))
    }

    fun startWorkout() = tap("Start workout")

    // A running workout

    fun seePhase(name: String) = see(name)

    /** Lets a running workout count on by [duration], which its next tick catches up in one step. */
    fun letTimePass(duration: Duration) = app.advance(duration)

    fun seeRound(round: String) = see(hasText(round, substring = true))

    fun pause() = tap("Pause")

    fun seePaused() {
        see("Paused")
        see(hasText("Resume") and hasClickAction())
    }

    fun resume() = tap("Resume")

    fun seeRunning() = see(hasText("Pause") and hasClickAction())

    fun skipPhase() = tapDescribed("Skip to next interval")

    /** Ends the workout from its End control, which asks first. */
    fun askToEnd() = tapDescribed("End workout")

    fun seeEndDialogSaying(words: String) {
        see("End workout?")
        see(hasText(words, substring = true) and hasAnyAncestor(isDialog()))
    }

    fun confirmEnd() = tap(hasText("End workout") and hasAnyAncestor(isDialog()))

    fun resumeFromDialog() = tap(hasText("Resume") and hasAnyAncestor(isDialog()))

    /** Starts the workout on the Timer and ends it once its first round is done: Get ready, Work, then Rest. */
    fun endWorkoutAfterFirstRound() {
        startWorkout()
        skipPhase()
        seePhase("Work")
        skipPhase()
        seePhase("Rest")
        askToEnd()
        seeEndDialogSaying("1 of 8 rounds done")
        confirmEnd()
    }

    fun seeSummarySaved() {
        see("Ended")
        see("Saved to history")
    }

    fun closeSummary() = tap("Done")

    // History and Plan

    fun seeHistoryEmpty() = see("Train to a plan")

    fun openPlanFromEmptyHistory() {
        tap("Plan your week")
        see("Weekly goal")
    }

    fun raiseWeeklyGoal() = tapDescribed("Raise the goal")

    fun seeWeeklyGoal(goal: Int) {
        waitFor("a weekly goal of $goal") { app.repository.appSettings.value.weeklyGoal == goal }
        see(goal.toString())
    }

    /** This week's words and its ring, which reads as "1 of 3 workouts this week". */
    fun seeThisWeek(done: Int, goal: Int) {
        see("${goal - done} more to hit $goal")
        see(hasContentDescription("$done of $goal workouts this week"))
    }

    fun openWorkoutUnderToday(name: String) {
        see("Today")
        tap(name)
        see("Repeat now")
    }

    fun seeEndedEarly() {
        see(hasText("ended early", substring = true))
        see("Plan again")
    }

    fun showMonth() = tap("Month")

    /** The month's calendar with one day marked done. The tests' formatter names a day "day N", not its date. */
    fun seeOneDayDoneOnMonth() = waitFor("one day marked done on the month") {
        ui.onAllNodes(hasContentDescription(", done", substring = true)).fetchSemanticsNodes().size == 1 &&
            ui.onAllNodes(hasText("Previous month") or hasContentDescription("Previous month"))
                .fetchSemanticsNodes().isNotEmpty()
    }

    fun clearHistory() {
        tapDescribed("More options")
        tap("Clear history")
        see("Clear history?")
        tap(hasText("Clear") and hasAnyAncestor(isDialog()))
    }

    // Presets

    fun seeTimerTotalFor(name: String) = see("$name · Total")

    fun openAllPresets() {
        tapDescribed("All presets")
        see("Built-in")
    }

    fun seeUnsavedTimerCard(reading: String) {
        see("On the Timer · not saved")
        see(hasText(reading, substring = true))
    }

    fun seeNoUnsavedTimerCard() = seeNo("On the Timer · not saved")

    fun seeNothingSavedYet() = see("Nothing saved yet")

    /** Opens Save as preset from the unsaved card at the top of Presets. */
    fun startSavingTimerSettings() {
        tap("Save")
        see("Save as preset")
    }

    fun namePreset(name: String) = ui.onNode(hasSetTextAction()).performTextReplacement(name)

    fun seeSavesAs(name: String) = see("Saves as $name")

    fun seeNameCount(count: String) = see(count)

    /** Saves the preset sheet: its Save is drawn last, over the card's own. */
    fun savePresetSheet() = tap("Save")

    fun seePresetOnTimer(name: String) = see(hasText(name) and hasText("On the Timer"))

    fun seePresetReading(name: String, reading: String) = see(hasText(name) and hasText(reading, substring = true))

    fun openPreset(name: String) {
        tap(name)
        see("Start workout")
    }

    fun putOnTimer() = tap("Put on Timer")

    fun seeNoPutOnTimer() = seeNo("Put on Timer")

    fun dismissSheet() = pressSystemBack()

    fun editPresetRounds(rounds: Int) {
        tap("Edit")
        see("Edit preset")
        tap("Rounds")
        tap("$rounds")
        tap("Done")
        see("×$rounds")
        savePresetSheet()
    }

    fun askToDeletePreset(name: String) {
        tap("Delete")
        see("Delete $name?")
        see("History keeps the workouts you did with it. The Timer keeps its settings.")
    }

    fun cancelDialog() = tap(hasText("Cancel") and hasAnyAncestor(isDialog()))

    fun confirmDelete() = tap(hasText("Delete") and hasAnyAncestor(isDialog()))

    fun openNewPreset(placeholder: String) {
        tapDescribed("New preset")
        see("New preset")
        see(placeholder)
    }

    fun cancelSheet() = tap("Cancel")

    fun seeSaveAsPresetOnTimer() = see(hasContentDescription("Save as preset"))

    fun savedPresets(vararg names: String) =
        assertEquals(names.toList(), app.repository.customPresets.value.map { it.name })

    // Settings

    fun openSettings() {
        tapDescribed("Settings")
        see("Cues")
    }

    fun toggleVoice() = tap("Voice")

    fun seeVoice(on: Boolean) {
        waitFor("voice cues ${if (on) "on" else "off"}") { app.repository.cueSettings.value.voice == on }
        if (on) ui.onNodeWithText("Voice").assertIsOn() else ui.onNodeWithText("Voice").assertIsOff()
    }

    /** Picks [name] ("System", "Light" or "Dark") in the theme picker. */
    fun pickTheme(name: String) = tap(name)

    fun seeTheme(name: String) {
        waitFor("the $name theme") { app.repository.appSettings.value.theme.name == name }
        ui.onNode(hasText(name) and hasClickAction()).assertIsSelected()
    }

    // Outcomes

    fun nothingIsSaved() = savedWorkouts(0)

    fun savedWorkouts(count: Int) = assertEquals(count, app.repository.history.value.size)
}

/**
 * An acceptance test: the whole app over fakes in [window], driven through [IntervalAcceptanceDriver]. Two
 * scenarios over one [storage] are the app before and after a restart.
 */
fun acceptanceScenario(
    window: Window = Window.Phone,
    storage: FakeStorage = FakeStorage(),
    scenario: suspend IntervalAcceptanceDriver.() -> Unit,
) = appTest(window, storage) { app -> IntervalAcceptanceDriver(this, app).scenario() }
