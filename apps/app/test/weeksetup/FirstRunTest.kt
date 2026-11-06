package dev.justinsmith.interval.weeksetup

import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.tabataPreset
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.model.asPlan
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.fakes.logSet
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirstRunTest {
    private val intervals = FakeStorage().repository()
    private val lift = FakeLiftStorage().repository()

    private val nothing get() = nothingYet(intervals, lift)

    @Test
    fun `set up has its turn on the first plain open the app offers it on`() {
        assertTrue(isSetupTurn(offers = true, byShortcut = false, offered = false))
        // A shortcut says where to go: the turn waits for a plain open.
        assertFalse(isSetupTurn(offers = true, byShortcut = true, offered = false))
        assertFalse(isSetupTurn(offers = true, byShortcut = false, offered = true))
        assertFalse(isSetupTurn(offers = false, byShortcut = false, offered = false))
    }

    @Test
    fun `it opens on set up only with nothing in the app`() {
        assertTrue(opensOnSetup(turn = true, nothing = true))
        assertFalse(opensOnSetup(turn = true, nothing = false))
        assertFalse(opensOnSetup(turn = false, nothing = true))
    }

    @Test
    fun `a new install has nothing in it`() {
        assertTrue(nothing)
    }

    @Test
    fun `a workout done is something`() {
        intervals.history.recordWorkout(IntervalConfig(), elapsedSeconds = 120)
        assertFalse(nothing)
    }

    @Test
    fun `a sport day logged is something`() {
        intervals.history.logSport(Sport.Bjj, 20_000)
        assertFalse(nothing)
    }

    @Test
    fun `a plan is something`() {
        intervals.plans.planWeekly(tabataPreset.asPlan(), setOf(1))
        assertFalse(nothing)
    }

    @Test
    fun `a preset saved is something`() {
        intervals.presets.savePreset(Preset("Hills", IntervalConfig(workSeconds = 45)))
        assertFalse(nothing)
    }

    @Test
    fun `a goal set is something`() {
        intervals.settings.updateAppSettings { it.copy(weeklyGoal = 3) }
        assertFalse(nothing)
    }

    @Test
    fun `a session on lift is something whether going or finished`() {
        lift.sessions.startSession()
        assertFalse(nothing)
        lift.sets.logSet(BuiltInExercise.TwoHandSwing, bellKg = 16.0, reps = 10, side = null)
        lift.sessions.finishSession("Swings")
        assertFalse(nothing)
    }

    @Test
    fun `a program followed is something`() {
        lift.programs.follow(ProgramKey.BuiltIn(ProgramId.ProgramMinimum))
        assertFalse(nothing)
    }

    @Test
    fun `a bell owned is something`() {
        lift.gear.setOwned(16.0, owned = true)
        assertFalse(nothing)
    }
}
