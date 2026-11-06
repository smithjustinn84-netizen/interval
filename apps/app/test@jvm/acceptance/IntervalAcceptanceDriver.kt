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
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithText
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
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.MILLIS_PER_SECOND
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.matching
import dev.justinsmith.interval.ui.AcceptanceSteps
import dev.justinsmith.interval.ui.JOURNEY_TIMEOUT
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.performClick
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlin.test.assertEquals
import kotlin.time.Duration

/**
 * The interval side's acceptance tests in the words of its journeys (the files in `journeys/`): each operation is
 * something a person does or sees, and the English it matches lives here, not in the specs.
 *
 * The rules: one selector per control, every check waits for the state to arrive (the view models emit on
 * Swing's thread), every tap goes in on that thread, outcomes are checked in the repository as well as on
 * screen, and time moves only through [AppFixture.advance].
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

    /** Train's Strength pillar, Lift's side: where a session starts. */
    fun seeLiftTab() = seeStrength()

    fun seeStrength() {
        see("Start session")
        seePillar("Strength")
    }

    /** Train's Conditioning pillar: the presets. */
    fun seeConditioning() {
        see("Built-in")
        seePillar("Conditioning")
    }

    /** Train's Mobility pillar: the flows, by when each is for. */
    fun seeMobility() {
        see("Before training")
        seePillar("Mobility")
    }

    /** A flow's sheet, from its row on Mobility: what is in it, with its Start. */
    fun openFlow(name: String) {
        tap(name)
        see(hasText("Each move is named and counted as you go", substring = true))
    }

    fun seeMoveInSheet(move: String, time: String) {
        see(move)
        see(time)
    }

    fun startFlow() = tap("Start")

    /** The move on the workout screen, held or coming, and where it is among the flow's. */
    fun seeMove(name: String, count: String) {
        see(name)
        see(hasText(count, substring = true))
    }

    fun seeNextMove(name: String) = see("Next: $name")

    fun seeSide(side: String) = see(side)

    /** The flow's summary, and the workout History keeps of it: every move done, as mobility. */
    fun seeFlowSaved(name: String, moves: Int) {
        see("Saved to your log")
        see(hasText("$name · Mobility · $moves moves"))
        val kept = app.repository.history.history.value.last()
        assertEquals(name to false, app.repository.flows.flows.value.matching(kept.config)?.name to kept.endedEarly)
    }

    /** A flow leaves the Timer's own settings alone, where a preset replaces them. */
    fun timerIsAsItWas() = assertEquals(IntervalConfig(), app.repository.settings.config.value)

    fun seeFlowInHistory(name: String, detail: String) {
        see(name)
        see(hasText(detail, substring = true))
    }

    /** From a finished workout's page in History. */
    fun repeatNow() = tap("Repeat now")

    /** Train's other pillar, by the picker under its title. */
    fun pickPillar(name: String) = tap(hasText(name) and isSelectable())

    private fun seePillar(name: String) {
        see(hasText(name) and isSelectable() and isSelected())
        ui.tab("Train").assertIsSelected()
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

    /** The Timer where it lives: Train, its Conditioning pillar, then the Timer row at the top of the presets. */
    fun openTimer() {
        openTab("Train")
        pickPillar("Conditioning")
        tap("Timer")
        seeTimer()
    }

    /** From Conditioning: the Timer row at the top of the presets. */
    fun openTimerFromPresets() {
        tap("Timer")
        seeTimer()
    }

    /** Back from the Timer to the presets it sits over, where the bar is again. */
    fun leaveTimer() {
        goBack()
        seeConditioning()
    }

    /** The Timer's page: its Start, and a Back to the presets it was opened from. */
    fun seeTimer() {
        see("Start workout")
        see(hasContentDescription("Describe a workout") and hasClickAction())
        see(hasContentDescription("Back") and hasClickAction())
    }

    fun seeLogTab() {
        see(hasContentDescription("Plan"))
        ui.tab("Log").assertIsSelected()
    }

    // Today

    fun seeTodayTab() {
        see("This week")
        ui.tab("Today").assertIsSelected()
    }

    fun seeNothingPlanned() = see("Nothing planned")

    /** The plan has nothing left for now: not the first run's card, which offers a first plan. */
    fun seeNothingMoreDue() {
        see("Nothing more due")
        seeNo("Plan your week")
    }

    fun openPlanFromToday() {
        tap("Plan your week")
        see("Weekly goal")
    }

    /** Or pick by time: one of its lengths, which the presets under it then fit. */
    fun pickMinutes(minutes: Int) = tap(hasText("$minutes min") and isSelectable())

    fun seePick(name: String) = see(name)

    /** A pick by time, by its row, which opens a look at it, then its Start. */
    fun startPick(name: String) {
        tap(hasText(name) and hasClickAction())
        see("Plan for later")
        tap("Start")
    }

    fun startSessionFromToday() = tap("Start session")

    /** Up next's line says about how long the program's day takes, before its day and its lifts. */
    fun seeUpNextTakes(words: String) = see(hasText(words, substring = true))

    /** Today's block on the week strip, read as done. */
    fun seeTodayDoneOnTheWeek() = see(hasContentDescription("Today, done"))

    /** The Timer was handed this shape, which the workout then runs. */
    fun timerHolds(workSeconds: Int, restSeconds: Int, rounds: Int) =
        waitFor("the Timer holding $workSeconds s on, $restSeconds s off, $rounds rounds") {
            app.repository.settings.config.value.let {
                it.workSeconds == workSeconds && it.restSeconds == restSeconds && it.rounds == rounds
            }
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

    /** The running workout's phase, once its page is the only one on screen: the Timer under it says "Work" too. */
    fun seePhase(name: String) {
        seeNo("Start workout")
        see(name)
    }

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
        see("Saved to your log")
    }

    fun closeSummary() = tap("Done")

    // A save that fails

    /** From here the disk is full: every workout recorded in History fails to save. */
    fun diskIsFull() {
        app.storage.history.failWrites = true
    }

    fun seeSaveFailed() = see("Couldn't save. Try again.")

    // History and Plan

    fun seeLogEmpty() = see("Train to a plan")

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
        // The day's heading on the log: the Today tab's label is on screen everywhere.
        see(hasText("Today") and isHeading())
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

    /** On Plan: a weekday's + and the add sheet's Sport side, one of the sports it names, then Add to plan. */
    fun planSportEvery(sport: String, weekday: String) {
        tapDescribed("Add a workout every $weekday")
        tap(hasText("Sport") and isSelectable())
        tap(hasText(sport) and isSelectable())
        tap(hasText("Add to plan") and hasClickAction())
        waitFor("a sport day planned") {
            app.repository.plans.planned.value.any { it.workout is PlanWorkout.SportDay }
        }
    }

    /** The day's chip on Plan, by the name the plan gives the sport's day. */
    fun seeSportPlannedOn(name: String, weekday: String) = see(hasContentDescription("Remove $name from $weekday"))

    /** A day of the week strip as it is read out, its sport after its state: "Today, BJJ planned". */
    fun seeSportOnTheWeek(day: String) = see(hasContentDescription(day))

    /** Today's Log it: the class's own when it leads, or its row's under "Also today". */
    fun logClassFromToday() = tap("Log it")

    /** What stands beside the lead on a class day, under its own heading. */
    fun seeAlsoToday(name: String) {
        see("Also today")
        see(name)
    }

    /** A flow to do before the day's sport, leading Today under what it is before. */
    fun seeFlowLeads(before: String, name: String) {
        see(before)
        see(name)
        see("Start workout")
    }

    fun seeNoFlowSuggested() = seeNo(hasText("Before ", substring = true))

    /** The lead's own Start: the flow runs, with the Timer left as it is. */
    fun startFlowFromToday() = tap("Start workout")

    /** The program's day as Today's lead: its Start is the big one, by the day's number. */
    fun seeProgramDayLeads() = see(hasText("Start day", substring = true))

    /** The program's day stood back beside the class: no Start of its own, only "Do it now". */
    fun seeProgramDayStandsBack() {
        seeNo(hasText("Start day", substring = true))
        see("Do it now")
    }

    fun doProgramDayNow() = tap("Do it now")

    /** Lifting is open on a session, with a set to log. */
    fun seeLiftingPage() = see("Log set")

    /** A notice's Undo, while it shows. */
    fun undoNotice() = tap("Undo")

    fun sportDaysLogged(count: Int) = waitFor("$count sport day(s) logged") {
        app.repository.history.sportDays.value.size == count
    }

    /** Today's count against the goal, in the week card's own words. */
    fun seeThisWeekOnToday(words: String) = see(hasText(words, substring = true))

    /** History's Plan action, in its title bar while the log or a plan gives it one. */
    fun openPlanFromLog() {
        tapDescribed("Plan")
        see("Weekly goal")
    }

    /** A day of the week strip, by how it is read out, which opens its sheet. */
    fun openDayOnTheWeek(day: String) = tap(hasContentDescription(day))

    /** The day sheet's Log it, on the class planned that day. */
    fun logClassFromItsDay() = tap("Log it")

    /** The day sheet lists the class among what was done, with the way to take it back. */
    fun seeClassLoggedOnItsDay(name: String) = see(hasContentDescription("Remove $name") and hasClickAction())

    fun removeLoggedClass(name: String) = tapDescribed("Remove $name")

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
        tap("Clear the log")
        see("Clear the log?")
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
        see("Your log keeps the workouts you did with it. The Timer keeps its settings.")
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

    /** Settings' Plan group reads a weekly goal of [goal], or "Off" for none, and that is what is kept. */
    fun seeGoalInSettings(goal: Int?) {
        waitFor("a weekly goal of $goal") { app.repository.settings.appSettings.value.weeklyGoal == goal }
        scrollTo("Weekly goal")
        see(hasText("Weekly goal") and hasText(goal?.toString() ?: "Off"))
    }

    /** Settings' Plan group reads a reminder at [time] ("18:00"), or "Off" for none. */
    fun seeReminderInSettings(time: String?) {
        scrollTo("Reminder")
        see(hasText("Reminder") and hasText(time ?: "Off"))
    }

    /** From Settings' Plan group to the Plan page, where the goal and the reminder are changed. */
    fun openPlanFromSettings() {
        scrollTo("Weekly goal")
        tap(hasText("Weekly goal") and hasClickAction())
        see(hasContentDescription("Raise the goal"))
    }

    /** On Plan: opens the reminder's sheet, picks [time] from its quick picks and closes it with Done. */
    fun remindAt(time: String) {
        scrollTo("Reminder")
        tap(hasText("Reminder") and hasClickAction())
        tap(time)
        tap("Done")
        // The sheet has gone, so what comes next acts on the page under it.
        seeNo("Quick picks")
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

    fun toggleVoice() {
        scrollTo("Voice")
        tap("Voice")
    }

    fun seeVoice(on: Boolean) {
        waitFor("voice cues ${if (on) "on" else "off"}") { app.repository.settings.cueSettings.value.voice == on }
        scrollTo("Voice")
        if (on) ui.onNodeWithText("Voice").assertIsOn() else ui.onNodeWithText("Voice").assertIsOff()
    }

    /** Picks [name] ("System", "Light" or "Dark") in the theme picker. */
    fun pickTheme(name: String) {
        scrollTo("Theme")
        tap(name)
    }

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
        scrollTo("Theme")
        ui.onNode(hasText(name) and hasClickAction()).assertIsSelected()
    }

    // Outcomes

    fun nothingIsSaved() = savedWorkouts(0)

    fun nothingSavedAsPreset() = waitFor("no preset saved") { app.repository.presets.customPresets.value.isEmpty() }

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

    /** Add a block, on the sets' sheet: the editor opens on the Timer's workout with a second block, as the first. */
    fun addBlockOnTimer() {
        tap("Sets")
        tap("Add a block")
        see("Edit workout")
        see("Block 2")
    }

    /** The last block's work, picked on its sheet from the editor: [clock] is the quick pick's, "1:00". */
    fun setLastBlockWork(clock: String) {
        val work = app.repository.settings.config.value.workSeconds
        tap(formatClock(work))
        tap(clock)
        tap("Done")
    }

    /** The editor's Done: the Timer takes the workout, saving no preset. */
    fun finishEditingWorkout(blocks: Int) {
        tap("Done")
        waitFor("the Timer's workout of $blocks blocks") { app.repository.settings.config.value.blocks.size == blocks }
        see("Edit blocks")
    }

    fun workoutKeptAsSequence(blocks: Int) = waitFor("a workout kept as a sequence of $blocks blocks") {
        app.repository.history.history.value.any { it.config.blocks.size == blocks }
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
 * scenarios over one [storage] are the app before and after a restart. It opens on Today, as an app does that
 * has been opened before; with [firstRun] it opens as a new install does, on Set up while there is nothing in it.
 */
fun acceptanceScenario(
    window: Window = Window.Phone,
    storage: FakeStorage = FakeStorage(),
    lift: FakeLiftStorage = FakeLiftStorage(),
    firstRun: Boolean = false,
    scenario: suspend IntervalAcceptanceDriver.() -> Unit,
) = appTest(window, storage, lift, timeout = JOURNEY_TIMEOUT, firstRun = firstRun) { app ->
    IntervalAcceptanceDriver(this, app).scenario()
}
