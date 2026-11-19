package dev.justinsmith.interval.today

import dev.justinsmith.interval.history.HistoryDay
import dev.justinsmith.interval.history.UpNextItem
import dev.justinsmith.interval.history.todayAgenda
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.sensor.SensorCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SensorCardTest {
    private val ready = SensorCard.Ready("Polar Sense")
    private val recording = SensorCard.Recording("18:02")

    private fun plan(vararg steps: UpNextItem, done: List<DoneStep> = emptyList()) =
        todayPlan(todayAgenda(steps.toList()), done)

    private fun sportToday(sport: Sport) = UpNextItem.SportDay(planId = 1, epochDay = 0, HistoryDay.Today, sport)

    @Test
    fun `recording a class is offered on a day with a sport still to log`() {
        assertEquals(ready, plan(sportToday(Sport.Bjj)).sensorCard(ready))
    }

    @Test
    fun `a day with no sport to log says nothing of the sensor`() {
        assertNull(plan().sensorCard(ready))
        assertNull(plan(done = listOf(DoneStep.SportLogged(Sport.Bjj))).sensorCard(ready))
    }

    @Test
    fun `a class on the sensor is there to sync whatever the day holds`() {
        assertEquals(recording, plan().sensorCard(recording))
        assertEquals(recording, plan(done = listOf(DoneStep.SportLogged(Sport.Bjj))).sensorCard(recording))
    }

    @Test
    fun `with no sensor paired there is no card`() {
        assertNull(plan(sportToday(Sport.Bjj)).sensorCard(null))
    }

    @Test
    fun `a class is of the sport still to log else the one logged else BJJ`() {
        assertEquals(Sport.Climbing, plan(sportToday(Sport.Climbing)).classSport)
        assertEquals(Sport.Running, plan(done = listOf(DoneStep.SportLogged(Sport.Running))).classSport)
        assertEquals(Sport.Bjj, plan().classSport)
    }
}
