package dev.justinsmith.lift.tests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.days
import dev.justinsmith.lift.data.GearStore
import dev.justinsmith.lift.data.IntervalActivity
import dev.justinsmith.lift.data.IntervalWorkout
import dev.justinsmith.lift.data.SessionStore
import dev.justinsmith.lift.data.SportDay
import dev.justinsmith.lift.data.snatchSets
import dev.justinsmith.lift.domain.Attempt
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.Readiness
import dev.justinsmith.lift.domain.Retest
import dev.justinsmith.lift.domain.StrengthTest
import dev.justinsmith.lift.domain.TestId
import dev.justinsmith.lift.domain.at
import dev.justinsmith.lift.domain.attempts
import dev.justinsmith.lift.domain.gripLoad
import dev.justinsmith.lift.domain.readiness
import dev.justinsmith.lift.domain.retest
import dev.justinsmith.lift.domain.sets
import dev.justinsmith.lift.domain.standardBells
import dev.justinsmith.lift.domain.startingBellKg
import dev.justinsmith.lift.domain.strengthTest
import dev.justinsmith.lift.domain.strengthTests
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.math.abs

/**
 * A test as the page lists it: every attempt at it, oldest first, whether another is worth doing ([retest]) and
 * the days since the last ([daysSince], null with none). [bells] are the sizes it can be done at, none for a test
 * with no bell, and [bellKg] the one its sheet opens on: the last attempt's, else what its lift starts at.
 */
data class TestRow(
    val test: StrengthTest,
    val attempts: List<Attempt> = emptyList(),
    val retest: Retest = Retest.Never,
    val daysSince: Int? = null,
    val bells: List<Double> = emptyList(),
    val bellKg: Double? = null,
) {
    val last: Attempt? get() = attempts.lastOrNull()

    /** The load an attempt at [kg] is done with: a bell or the test's pair of them; null for a test with no bell. */
    fun loadAt(kg: Double?): Load? =
        kg?.takeIf { test.exercise.equipment == Equipment.Kettlebell }?.let { Load.Bell(it, test.block.double) }

    /** The attempts at [kg], or with no bell every one: those a result there compares with. */
    fun attemptsAt(kg: Double?): List<Attempt> = loadAt(kg)?.let { attempts.at(it) } ?: attempts
}

/**
 * The tests, in the page's order; whether today is a day to leave one ([taxed], as [readiness] has it) and whether
 * a session is in progress, which a test can't start over.
 */
data class TestsUiState(
    val tests: List<TestRow> = strengthTests.map { TestRow(it) },
    val taxed: Boolean = false,
    val inProgress: Boolean = false,
) {
    fun row(id: TestId): TestRow = tests.first { it.test.id == id }
}

/** The tests and the attempts at each, worked out from the sessions; and starting one. */
class TestsViewModel(
    private val sessionStore: SessionStore,
    gearStore: GearStore,
    intervals: IntervalActivity,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
    dayCheckMillis: Long = DAY_CHECK_MILLIS,
) : ViewModel() {
    val state: StateFlow<TestsUiState> =
        combine(
            sessionStore.sessions,
            sessionStore.active,
            combine(gearStore.bells, gearStore.pairs, ::Pair),
            combine(intervals.sportDays, intervals.workouts, ::Pair),
            clock.days(timestamps, dayCheckMillis),
        ) { sessions, active, gear, (sportDays, workouts), _ -> build(sessions, active, gear, sportDays, workouts) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                build(
                    sessionStore.sessions.value,
                    sessionStore.active.value,
                    gearStore.bells.value to gearStore.pairs.value,
                    intervals.sportDays.value,
                    intervals.workouts.value,
                ),
            )

    /**
     * Starts a session of [id]'s test at the bell [kg] (null for a test with none), unless one is in progress:
     * the page then opens the session.
     */
    fun start(id: TestId, kg: Double?) {
        val row = state.value.row(id)
        sessionStore.startTest(strengthTest(id), row.loadAt(kg))
    }

    private fun build(
        sessions: List<LiftSession>,
        active: LiftSession?,
        gear: Pair<List<Double>, Set<Double>>,
        sportDays: List<SportDay>,
        workouts: List<IntervalWorkout>,
    ): TestsUiState {
        val (owned, pairs) = gear
        val now = clock.nowMillis()
        val today = timestamps.dayNumber(now)
        val grappling = sportDays.filter { it.grappling }.map { it.epochDay }
        // 15:15's snatches load the grip as Lift's own sets do.
        val lifted = sessions.flatMap { it.sets } + workouts.snatchSets.sets
        val grip = gripLoad(lifted, grappling, today, timestamps::dayNumber)
        return TestsUiState(
            tests = strengthTests.map { test ->
                val done = attempts(test, sessions)
                val bells = bellsFor(test, owned, pairs)
                TestRow(
                    test,
                    done,
                    retest(done, now),
                    done.lastOrNull()?.let {
                        (today - timestamps.dayNumber(it.atEpochMillis)).toInt().coerceAtLeast(0)
                    },
                    bells,
                    bells.takeIf { it.isNotEmpty() }?.let { sizes ->
                        val wanted = done.lastOrNull()?.load?.kg ?: startingBellKg(test.exercise, owned)
                        sizes.firstOrNull { it == wanted } ?: sizes.minBy { abs(it - wanted) }
                    },
                )
            },
            taxed = readiness(sessions, grappling, grip, now, timestamps::dayNumber).readiness == Readiness.Taxed,
            inProgress = active != null,
        )
    }

    /**
     * The bells [test] can be done at: none for one with no bell; the [pairs] for one done with two, once any is
     * said; else those [owned], or every standard size with none said.
     */
    private fun bellsFor(test: StrengthTest, owned: List<Double>, pairs: Set<Double>): List<Double> = when {
        test.exercise.equipment != Equipment.Kettlebell -> emptyList()
        test.block.double && pairs.isNotEmpty() -> pairs.sorted()
        owned.isNotEmpty() -> owned
        else -> standardBells
    }
}
