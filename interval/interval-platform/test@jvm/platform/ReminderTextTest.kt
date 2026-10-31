package dev.justinsmith.interval.platform

import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.timer.IntervalConfig
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** The notifications say these words on both platforms; common tests record [PlanReminder]s only. */
class ReminderTextTest {
    private val tabata = PlannedWorkout(
        1,
        PlanWorkout.Interval(
            "Tabata",
            IntervalConfig(prepSeconds = 10, workSeconds = 20, restSeconds = 10, rounds = 8),
        ),
        PlanSchedule.Once(0),
    )
    private val efforts = PlannedWorkout(
        2,
        PlanWorkout.Interval(
            "Long efforts",
            IntervalConfig(prepSeconds = 10, workSeconds = 180, restSeconds = 60, rounds = 5),
        ),
        PlanSchedule.Once(0),
    )
    private val programDay = PlannedWorkout(3, PlanWorkout.Lift.ProgramDay, PlanSchedule.Once(0))
    private val anySession = PlannedWorkout(4, PlanWorkout.Lift.AnySession, PlanSchedule.Once(0))

    @Test
    fun `one workout is named with its shape under it`() = runBlocking {
        assertEquals(
            ReminderWords("Tabata", "Planned today · 20s on · 10s off · ×8"),
            reminderWords(PlanReminder(0, 420, listOf(tabata))),
        )
        assertEquals(
            ReminderWords("Long efforts", "Planned today · 3m on · 1m off · ×5"),
            reminderWords(PlanReminder(0, 420, listOf(efforts))),
        )
    }

    @Test
    fun `several are counted with their names under the count`() = runBlocking {
        assertEquals(
            ReminderWords("2 workouts planned today", "Tabata · Long efforts"),
            reminderWords(PlanReminder(0, 420, listOf(tabata, efforts))),
        )
    }

    @Test
    fun `a session on Lift is named as one`() = runBlocking {
        assertEquals(
            ReminderWords("Lift session", "Planned today · the program’s next day"),
            reminderWords(PlanReminder(0, 420, listOf(programDay))),
        )
        assertEquals(
            ReminderWords("Lift session", "Planned today · any session on Lift"),
            reminderWords(PlanReminder(0, 420, listOf(anySession))),
        )
        assertEquals(
            ReminderWords("2 workouts planned today", "Tabata · Lift session"),
            reminderWords(PlanReminder(0, 420, listOf(tabata, programDay))),
        )
    }
}
