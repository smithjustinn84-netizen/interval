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
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import dev.justinsmith.interval.AppFixture
import dev.justinsmith.interval.appTest
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.RecordingCuePlayer
import dev.justinsmith.interval.fakes.RecordingHealthStore
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.nav.Shortcut
import dev.justinsmith.interval.platform.CuePlayer
import dev.justinsmith.interval.platform.HealthSegment
import dev.justinsmith.interval.platform.HealthStatus
import dev.justinsmith.interval.platform.HealthStore
import dev.justinsmith.interval.platform.HealthWorkoutKind
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.platform.ShareableFile
import dev.justinsmith.interval.platform.Speech
import dev.justinsmith.interval.tab
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.MILLIS_PER_SECOND
import dev.justinsmith.interval.ui.AcceptanceSteps
import dev.justinsmith.interval.ui.JOURNEY_TIMEOUT
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

    /** Opens the app from a launcher shortcut, as a long-press on its icon does, cold or already running. */
    fun launchFrom(shortcut: Shortcut) {
        app.shortcut.value = shortcut
        ui.waitForIdle()
    }

    fun seeLiftTab() {
        see("Start session")
        ui.tab("Lift").assertIsSelected()
    }

    /** A Lift session is stored as in progress: started by whatever opened its page, not by the page. */
    fun aLiftSessionIsInProgress() {
        waitFor("a session in progress") { app.lift.sessions.sessions.value.any { it.finishedAtEpochMillis == null } }
    }

    /** Leaves a Lift session from the dialog Back asks. */
    fun leaveSession() {
        see("Leave the session?")
        tap(hasText("Leave") and hasAnyAncestor(isDialog()))
    }

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

    fun setWarmUp(value: String) = setBookend("Warm-up", value)

    fun setCoolDown(value: String) = setBookend("Cool-down", value)

    /** Under the Timer's three rows, a chip (sets, warm-up or cool-down), then a quick pick on its sheet. */
    private fun setBookend(name: String, value: String) {
        tap(name)
        see("Quick picks")
        tap(value)
        tap("Done")
        see(hasText(name) and hasText(value) and hasClickAction())
    }

    /** Takes the Timer's rounds down to [rounds] a step at a time, as the − beside them does. */
    fun lowerRoundsTo(rounds: Int) {
        val now = app.repository.settings.config.value.rounds
        repeat(now - rounds) { tapDescribed("Fewer rounds") }
        see(hasText("×$rounds"))
    }

    /** The ladder, on Work's sheet: its + a step at a time to [seconds] a round, which the Work row then says. */
    fun setLadder(seconds: Int) {
        tap("Work")
        see("Ladder")
        val now = app.repository.settings.config.value.workStepSeconds
        repeat((seconds - now) / ConfigField.WorkStep.step) { tapDescribed("More work each round") }
        tap("Done")
        see("Work · +${seconds}s a round")
    }

    /** The workout History holds was kept as a ladder of [step] seconds a round. */
    fun workoutKeptAsLadder(step: Int) = waitFor("a workout kept as a ladder of $step s") {
        app.repository.history.history.value.any { it.config.workStepSeconds == step }
    }

    /** The sets chip under the Timer's rows, then a quick pick on its sheet. */
    fun setSets(count: String) = setBookend("Sets", count)

    /** The rest between sets, on the sets' sheet once there are two: its + a step at a time to [seconds]. */
    fun setRestBetweenSets(seconds: Int) {
        tap("Sets")
        see("Between sets")
        val now = app.repository.settings.config.value.setRestSeconds
        repeat((seconds - now) / ConfigField.SetRest.step) { tapDescribed("More rest between sets") }
        tap("Done")
        waitFor("a rest of $seconds s between sets") {
            app.repository.settings.config.value.setRestSeconds == seconds
        }
    }

    fun seeTimerTotal(total: String) = see(total)

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

    // A save that fails

    /** From here the disk is full: every workout recorded in History fails to save. */
    fun diskIsFull() {
        app.storage.history.failWrites = true
    }

    fun seeSaveFailed() = see("Couldn't save. Try again.")

    // History and Plan

    fun seeHistoryEmpty() = see("Train to a plan")

    fun openPlanFromEmptyHistory() {
        tap("Plan your week")
        see("Weekly goal")
    }

    fun raiseWeeklyGoal() = tapDescribed("Raise the goal")

    fun seeWeeklyGoal(goal: Int) {
        waitFor("a weekly goal of $goal") { app.repository.settings.appSettings.value.weeklyGoal == goal }
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

    /** On Plan: a weekday's + and the add sheet's Lift side, with no program followed, then Add to plan. */
    fun planAnyLiftSessionEvery(weekday: String) {
        tapDescribed("Add a workout every $weekday")
        tap(hasText("Lift") and isSelectable())
        see("No program followed")
        tap(hasText("Add to plan") and hasClickAction())
        waitFor("a session on Lift planned") {
            app.repository.plans.planned.value.singleOrNull()?.workout == PlanWorkout.Lift.AnySession
        }
    }

    fun seeLiftSessionPlannedOn(weekday: String) = see(hasContentDescription("Remove Any lift session from $weekday"))

    fun seeUpNext(name: String) {
        see(hasText("Up next", substring = true))
        see(name)
    }

    fun startUpNext() = tap("Start")

    /** On Lifting: one set logged a minute and a half in, then Finish and the summary's Done. */
    fun logASetAndFinish() {
        tap("Log set")
        app.clock.set(90_000)
        tap("Finish")
        see("Counts toward your week and your goal")
        tap("Done")
        waitFor("the session finished") { app.lift.sessions.sessions.value.any { it.finishedAtEpochMillis != null } }
    }

    /** Today on the week strip, which opens its day sheet. */
    fun openToday() = tap(hasContentDescription("Today, done"))

    fun seeTodaysPlanDone(repeat: String) = see("Done · $repeat")

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
        assertEquals(names.toList(), app.repository.presets.customPresets.value.map { it.name })

    // Settings

    fun openSettings() {
        tapDescribed("Settings")
        see("Cues")
    }

    /** Under Your data: Import, picking a file that holds [content] in the system's picker. */
    fun importFile(content: String) {
        app.files.next = ShareableFile("interval.json", "application/json", content)
        scrollTo("Import")
        tap(hasText("Import") and hasClickAction())
    }

    /** The review sheet says when the file was exported and what in it is [new], or that nothing is. */
    fun seeImportReview(exported: String, new: String) {
        see(hasText(exported, substring = true))
        see(new)
    }

    fun seeAlreadyHere(count: Int) = see(hasText("$count things in it are already here", substring = true))

    fun alsoSettings() = tap("Also settings")

    /** Imports the file under review, from the sheet's button: drawn over the row of the same name, so the last. */
    fun confirmImport() = tap(hasText("Import") and hasClickAction())

    fun seeImported(text: String) = see(text)

    fun seeImportRefused(why: String) {
        see(hasText(why, substring = true))
        tap("Done")
    }

    fun cancelImport() = tap("Cancel")

    fun savedPresetsNamed(vararg names: String) =
        assertEquals(names.toList(), app.repository.presets.customPresets.value.map { it.name })

    fun toggleVoice() = tap("Voice")

    fun seeVoice(on: Boolean) {
        waitFor("voice cues ${if (on) "on" else "off"}") { app.repository.settings.cueSettings.value.voice == on }
        if (on) ui.onNodeWithText("Voice").assertIsOn() else ui.onNodeWithText("Voice").assertIsOff()
    }

    /** Picks [name] ("System", "Light" or "Dark") in the theme picker. */
    fun pickTheme(name: String) = tap(name)

    // Health Connect

    private val healthStore: RecordingHealthStore get() = app.koin.koin.get<HealthStore>() as RecordingHealthStore

    /** Allows the writes on Health Connect's sheet, which only the platform can open: the store stands connected. */
    fun allowHealthConnect() {
        healthStore.status.value = HealthStatus.Connected
    }

    fun toggleHealthConnect() {
        scrollTo("Health Connect")
        tap("Health Connect")
    }

    fun seeHealthConnect(on: Boolean) {
        waitFor("Health Connect ${if (on) "on" else "off"}") {
            healthStore.status.value == if (on) HealthStatus.Connected else HealthStatus.Disconnected
        }
        scrollTo("Health Connect")
        if (on) ui.onNodeWithText("Health Connect").assertIsOn() else ui.onNodeWithText("Health Connect").assertIsOff()
    }

    /** The workout just finished is in Health Connect as one interval session, its course as segments. */
    fun seeWorkoutInHealthConnect() {
        waitFor("a workout in Health Connect") { healthStore.workouts.size == 1 }
        val workout = healthStore.workouts.single()
        assertEquals(HealthWorkoutKind.Intervals, workout.kind)
        assertEquals(
            app.repository.history.history.value.single().elapsedSeconds,
            workout.segments.sumOf {
                it.lengthSeconds
            },
        )
    }

    fun seeNothingMoreInHealthConnect() = assertEquals(1, healthStore.workouts.size)

    /** A scale or another app records a weight of [kg] in Health Connect. */
    fun weighInHealthConnect(kg: Double) {
        healthStore.weightKg = kg
    }

    /** A watch has recorded the heart rate in Health Connect, [average] and [peak] beats a minute, and synced. */
    fun watchSyncsHeartRate(average: Int, peak: Int) {
        healthStore.heartRate = HeartRate(average, peak)
    }

    /** The summary shows the workout's heart rate, its average and peak. */
    fun seeSummaryHeartRate(average: Int, peak: Int) = see("Heart rate $average avg · $peak peak")

    fun seeNoSummaryHeartRate() = seeNo(hasText("Heart rate", substring = true))

    /** A finished workout's page shows its heart rate, average and peak, and History has kept it. */
    fun seeWorkoutHeartRate(average: Int, peak: Int) {
        waitFor("a heart rate kept in History") { app.repository.history.history.value.single().averageBpm == average }
        see("$average bpm")
        see("$peak bpm")
    }

    /** Opens the body weight's sheet, takes Health Connect's [weight] from its button, and closes it with Done. */
    fun takeHealthConnectWeight(weight: String) {
        scrollTo("Body weight")
        tap("Body weight")
        tap("Use Health Connect's: $weight")
        see("From Health Connect: $weight")
        tap("Done")
    }

    /** Settings' body weight reads [kg] kilograms, and that is what is kept. */
    fun seeBodyWeight(kg: Int) {
        waitFor("a body weight of $kg kg") { app.repository.settings.appSettings.value.body.weightKg == kg.toDouble() }
        scrollTo("$kg kg")
    }

    fun seeTheme(name: String) {
        waitFor("the $name theme") { app.repository.settings.appSettings.value.theme.name == name }
        ui.onNode(hasText(name) and hasClickAction()).assertIsSelected()
    }

    // Outcomes

    fun nothingIsSaved() = savedWorkouts(0)

    /** On the summary, under "How was it?": how hard the workout felt, then a note on it. */
    fun sayHowItWas(effort: String, note: String) {
        see("How was it?")
        tap(hasText(effort) and isSelectable())
        ui.onNode(hasSetTextAction()).performScrollTo().performTextInput(note)
    }

    /** The workout History holds was kept with how it felt: an effort of [level], 1 to 5, and its [note]. */
    fun workoutKeptAs(level: Int, note: String) = waitFor("the workout kept as $level, \"$note\"") {
        app.repository.history.history.value.any { it.effort?.level == level && it.note == note }
    }

    /** The workout History holds was kept with its warm-up and cool-down, as part of its shape. */
    fun workoutKeptWithBookends(warmUpSeconds: Int, coolDownSeconds: Int) =
        waitFor("a workout kept with a $warmUpSeconds s warm-up and a $coolDownSeconds s cool-down") {
            app.repository.history.history.value.any {
                it.config.warmUpSeconds == warmUpSeconds && it.config.coolDownSeconds == coolDownSeconds
            }
        }

    /** The one workout History holds ran to its end: every round, not ended early. */
    fun workoutKeptFinished() = assertEquals(false, app.repository.history.history.value.single().endedEarly)

    fun savedWorkouts(count: Int) = assertEquals(count, app.repository.history.history.value.size)

    /** The workout History holds was kept as [sets] sets of [rounds]. */
    fun workoutKeptWithSets(sets: Int, rounds: Int) = waitFor("a workout kept as $sets sets of $rounds") {
        app.repository.history.history.value.any { it.config.sets == sets && it.config.rounds == rounds }
    }

    /** The voice said [speech]: the workout's cues, as the player was asked for them. */
    fun heard(speech: Speech) = waitFor("to hear $speech") { speech in cuePlayer.spoken }

    private val cuePlayer: RecordingCuePlayer get() = app.koin.koin.get<CuePlayer>() as RecordingCuePlayer
}

/** A segment's length in whole seconds, as the summary counts time. */
private val HealthSegment.lengthSeconds: Int
    get() = ((endEpochMillis - startEpochMillis) / MILLIS_PER_SECOND).toInt()

/**
 * An acceptance test: the whole app over fakes in [window], driven through [IntervalAcceptanceDriver]. Two
 * scenarios over one [storage] are the app before and after a restart.
 */
fun acceptanceScenario(
    window: Window = Window.Phone,
    storage: FakeStorage = FakeStorage(),
    scenario: suspend IntervalAcceptanceDriver.() -> Unit,
) = appTest(window, storage, timeout = JOURNEY_TIMEOUT) { app -> IntervalAcceptanceDriver(this, app).scenario() }
