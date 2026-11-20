package dev.justinsmith.interval.history

import dev.justinsmith.interval.model.Sport
import kotlin.test.Test
import kotlin.test.assertEquals

class SportLinesTest {
    private fun recorded(startedAt: Long, sport: Sport = Sport.Bjj) = ClassRow(startedAt, sport, rounds = 3, "45:00")

    private fun lines(sports: List<Sport>, classes: List<ClassRow>) =
        sportLines(sports, classes).map { Triple(it.sport, it.recorded?.startedAtEpochMillis, it.logged) }

    @Test
    fun `a sport logged has a row with its class or without one`() {
        assertEquals(listOf(Triple(Sport.Bjj, null, true)), lines(listOf(Sport.Bjj), emptyList()))
        assertEquals(listOf(Triple(Sport.Bjj, 5L, true)), lines(listOf(Sport.Bjj), listOf(recorded(5))))
    }

    @Test
    fun `a second class of a sport the same day keeps a row of its own`() {
        val rows = lines(listOf(Sport.Bjj), listOf(recorded(9), recorded(5)))

        // The day is taken off from its first row alone.
        assertEquals(listOf(Triple(Sport.Bjj, 9L, true), Triple(Sport.Bjj, 5L, false)), rows)
    }

    @Test
    fun `a class whose day is not logged keeps a row after the sports that are`() {
        val rows = lines(listOf(Sport.Climbing), listOf(recorded(5)))

        assertEquals(listOf(Triple(Sport.Climbing, null, true), Triple(Sport.Bjj, 5L, false)), rows)
    }
}
