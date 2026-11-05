@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.data

import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.saveCurrentAsPreset
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.asPlan
import dev.justinsmith.interval.timer.builtInPresets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The planned workouts through [PlanRepository]. */
class PlanRepositoryTest {
    private val storage = FakeStorage()
    private var clock = 1_000L
    private fun repository() = storage.repository(now = { clock })

    @Test
    fun `a removed plan restored keeps its id and the day it was made`() {
        val repository = repository()
        val tabata = builtInPresets.first()
        repository.plans.planWeekly(tabata.asPlan(), setOf(1, 3))
        clock += 500
        val monday = repository.plans.planned.value.first()
        repository.plans.removePlanned(monday.id)
        assertEquals(1, repository.plans.planned.value.size)
        repository.plans.restorePlanned(monday)
        assertEquals(listOf(monday, repository.plans.planned.value[1]), repository.plans.planned.value)
        assertEquals(1_000L, repository.plans.planned.value.first().createdAtEpochMillis)
    }

    @Test
    fun `a weekly plan is one plan per weekday and survives a restart`() {
        val tabata = builtInPresets.first()
        repository().plans.planWeekly(tabata.asPlan(), setOf(4, 1, 9))
        val planned = repository().plans.planned.value
        assertEquals(listOf(PlanSchedule.Weekly(1), PlanSchedule.Weekly(4)), planned.map { it.schedule })
        assertTrue(planned.all { it.workout == PlanWorkout.Interval(tabata.name, tabata.config, presetId = null) })
        assertTrue(planned.all { it.createdAtEpochMillis == clock })
    }

    @Test
    fun `a one-off plan keeps its day and the user's preset it came from`() {
        val repository = repository()
        repository.saveCurrentAsPreset("Mine")
        val mine = repository.presets.customPresets.value.single()
        repository.plans.planOnce(mine.asPlan(), epochDay = 20_744)
        val planned = repository().plans.planned.value.single()
        assertEquals(PlanSchedule.Once(20_744), planned.schedule)
        assertEquals(mine.id, (planned.workout as PlanWorkout.Interval).presetId)
        assertTrue(planned.fallsOn(20_744))
        assertFalse(planned.fallsOn(20_745))
    }

    @Test
    fun `a plan stays as made when its preset is deleted and can be removed`() {
        val repository = repository()
        repository.saveCurrentAsPreset("Mine")
        val mine = repository.presets.customPresets.value.single()
        repository.plans.planWeekly(mine.asPlan(), setOf(2))
        repository.presets.deletePreset(mine)
        val planned = repository.plans.planned.value.single()
        assertEquals("Mine", (planned.workout as PlanWorkout.Interval).name)
        repository.plans.removePlanned(planned.id)
        assertEquals(emptyList(), repository().plans.planned.value)
    }

    @Test
    fun `a session on Lift is planned and read back as one`() {
        repository().plans.planWeekly(PlanWorkout.Lift.ProgramDay, setOf(1, 3))
        repository().plans.planOnce(PlanWorkout.Lift.AnySession, epochDay = 20_744)
        val planned = repository().plans.planned.value
        assertEquals(
            listOf(PlanWorkout.Lift.ProgramDay, PlanWorkout.Lift.ProgramDay, PlanWorkout.Lift.AnySession),
            planned.map { it.workout },
        )
        assertEquals(PlanSchedule.Once(20_744), planned.last().schedule)
    }
}
