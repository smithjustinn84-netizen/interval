@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.plan

import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.RecordingPlanReminders
import dev.justinsmith.interval.fakes.TODAY
import dev.justinsmith.interval.fakes.noonOf
import dev.justinsmith.interval.fakes.tabataPreset
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Thursday 15 October 2026, at noon; the reminder is set for 18:00.

class PlanReminderSchedulerTest {
    @Test
    fun `the platform is handed the plan's reminders as they change`() = runTest {
        var now = noonOf(TODAY)
        val repository = FakeStorage().repository(now = { now })
        val reminders = RecordingPlanReminders()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        PlanReminderScheduler(repository, reminders, FixedTimestampFormatter, { now }, scope)
        runCurrent()

        // Off: nothing pending, and a plan changes nothing.
        assertEquals(listOf(emptyList()), reminders.schedules)
        repository.planWeekly(tabataPreset, setOf(4, 5))
        runCurrent()
        assertEquals(1, reminders.schedules.size)

        // On: Thursday and Friday this week and next, two weeks out.
        repository.updateAppSettings { it.copy(reminderMinuteOfDay = EVENING) }
        runCurrent()
        assertEquals(listOf(TODAY, TODAY + 1, TODAY + 7, TODAY + 8), reminders.pending.map { it.epochDay })
        assertTrue(reminders.pending.all { it.minuteOfDay == EVENING && it.plans.single().name == "Tabata" })

        // Today's workout done: today's reminder goes.
        repository.recordWorkout(tabataPreset.config, elapsedSeconds = 240)
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

    private companion object {
        const val EVENING = 18 * 60
    }
}
