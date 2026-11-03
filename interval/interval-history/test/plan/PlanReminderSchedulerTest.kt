@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.plan

import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.RecordingPlanReminders
import dev.justinsmith.interval.fakes.TODAY
import dev.justinsmith.interval.fakes.noonOf
import dev.justinsmith.interval.fakes.tabataPreset
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.asPlan
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import dev.justinsmith.interval.platform.PlanReminder
import dev.justinsmith.interval.platform.PlanReminders
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Thursday 15 October 2026, at noon; the reminder is set for 18:00.

class PlanReminderSchedulerTest {
    @Test
    fun `the platform is handed the plan's reminders as they change`() = runTest {
        var now = noonOf(TODAY)
        val repository = FakeStorage().repository(now = { now })
        val lift = FakeLiftStorage()
        val reminders = RecordingPlanReminders()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        PlanReminderScheduler(
            repository,
            lift.repository(now = {
                now
            }).sessions,
            reminders,
            FixedTimestampFormatter,
            { now },
            scope,
        )
        runCurrent()

        // Off: nothing pending, and a plan changes nothing.
        assertEquals(listOf(emptyList()), reminders.schedules)
        repository.planWeekly(tabataPreset.asPlan(), setOf(4, 5))
        runCurrent()
        assertEquals(1, reminders.schedules.size)

        // On: Thursday and Friday this week and next, two weeks out.
        repository.updateAppSettings { it.copy(reminderMinuteOfDay = EVENING) }
        runCurrent()
        assertEquals(listOf(TODAY, TODAY + 1, TODAY + 7, TODAY + 8), reminders.pending.map { it.epochDay })
        assertTrue(
            reminders.pending.all {
                it.minuteOfDay == EVENING &&
                    it.plans.single().workout == tabataPreset.asPlan()
            },
        )

        // Today's workout done: today's reminder goes.
        repository.recordWorkout(tabataPreset.config, elapsedSeconds = 240)
        runCurrent()
        assertEquals(listOf(TODAY + 1, TODAY + 7, TODAY + 8), reminders.pending.map { it.epochDay })

        // A session on Lift planned on Saturday is reminded of, and goes once a session is logged that day.
        repository.planOnce(PlanWorkout.Lift.AnySession, TODAY + 2)
        runCurrent()
        assertEquals(listOf(TODAY + 1, TODAY + 2, TODAY + 7, TODAY + 8), reminders.pending.map { it.epochDay })
        lift.finished("b1", "Swings", noonOf(TODAY + 2), noonOf(TODAY + 2) + 600_000, 24.0 to 10)
        runCurrent()
        assertEquals(listOf(TODAY + 1, TODAY + 7, TODAY + 8), reminders.pending.map { it.epochDay })

        // Midnight with the app open: the two weeks move on a day.
        now = noonOf(TODAY + 1)
        advanceTimeBy(DAY_CHECK_MILLIS + 1)
        assertEquals(listOf(TODAY + 1, TODAY + 7, TODAY + 8, TODAY + 14), reminders.pending.map { it.epochDay })

        // Off again: the platform is told to cancel them all.
        repository.updateAppSettings { it.copy(reminderMinuteOfDay = null) }
        runCurrent()
        assertTrue(reminders.pending.isEmpty())
        scope.cancel()
    }

    @Test
    fun `a set the platform can't take is skipped and the next change is scheduled`() = runTest {
        val now = noonOf(TODAY)
        val repository = FakeStorage().repository(now = { now })
        var failing = true
        val recorded = RecordingPlanReminders()
        val reminders = object : PlanReminders {
            override suspend fun schedule(reminders: List<PlanReminder>) {
                if (failing) error("disk full") else recorded.schedule(reminders)
            }
        }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val lift = FakeLiftStorage().repository(now = { now })
        PlanReminderScheduler(repository, lift.sessions, reminders, FixedTimestampFormatter, { now }, scope)
        runCurrent()

        failing = false
        repository.planWeekly(tabataPreset.asPlan(), setOf(4))
        repository.updateAppSettings { it.copy(reminderMinuteOfDay = EVENING) }
        runCurrent()
        assertEquals(listOf(TODAY, TODAY + 7), recorded.pending.map { it.epochDay })
        scope.cancel()
    }

    @Test
    fun `a reminder delivered moves the reminders on to the fifteenth day`() = runTest {
        var now = noonOf(TODAY)
        val repository = FakeStorage().repository(now = { now })
        val reminders = RecordingPlanReminders()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val lift = FakeLiftStorage().repository(now = { now })
        val scheduler =
            PlanReminderScheduler(repository, lift.sessions, reminders, FixedTimestampFormatter, { now }, scope)
        repository.planWeekly(tabataPreset.asPlan(), (1..7).toSet())
        repository.updateAppSettings { it.copy(reminderMinuteOfDay = EVENING) }
        runCurrent()
        assertEquals((TODAY..TODAY + 13).toList(), reminders.pending.map { it.epochDay })

        // A day on, with the app not opened since: no midnight passed in it, only the alarm.
        now = noonOf(TODAY + 1)
        scheduler.reschedule()
        assertEquals((TODAY + 1..TODAY + 14).toList(), reminders.pending.map { it.epochDay })

        // The same set again (the clock or time zone changed) is still handed over, to be set anew.
        val handed = reminders.schedules.size
        scheduler.reschedule()
        assertEquals(handed + 1, reminders.schedules.size)
        scope.cancel()
    }

    @Test
    fun `the reminder showing is the day's open plans as they stand`() = runTest {
        val now = noonOf(TODAY) + 7 * 60 * 60 * 1000L
        val repository = FakeStorage().repository(now = { now })
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val lift = FakeLiftStorage().repository(now = { now })
        val scheduler =
            PlanReminderScheduler(repository, lift.sessions, RecordingPlanReminders(), FixedTimestampFormatter, {
                now
            }, scope)
        repository.planWeekly(tabataPreset.asPlan(), setOf(4))
        repository.updateAppSettings { it.copy(reminderMinuteOfDay = EVENING) }
        runCurrent()

        // Shown at 19:00, past its time, it is still today's.
        assertEquals(tabataPreset.asPlan(), scheduler.reminderOn(TODAY)?.plans?.single()?.workout)
        repository.recordWorkout(tabataPreset.config, elapsedSeconds = 240)
        assertNull(scheduler.reminderOn(TODAY))
        repository.updateAppSettings { it.copy(reminderMinuteOfDay = null) }
        assertNull(scheduler.reminderOn(TODAY + 7))
        scope.cancel()
    }

    private companion object {
        const val EVENING = 18 * 60
    }
}
