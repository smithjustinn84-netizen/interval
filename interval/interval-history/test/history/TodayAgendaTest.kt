package dev.justinsmith.interval.history

import dev.justinsmith.interval.fakes.TODAY
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.program
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TodayAgendaTest {
    private val programDay = program(ProgramId.FifteenMinuteKettlebell).day(1)

    private val bjj = UpNextItem.SportDay(1, TODAY, HistoryDay.Today, Sport.Bjj)
    private val climbing = UpNextItem.SportDay(2, TODAY, HistoryDay.Today, Sport.Climbing)
    private val tabata = UpNextItem.Planned(3, TODAY, HistoryDay.Today, "Tabata", IntervalConfig())
    private val tomorrow = UpNextItem.Planned(4, TODAY + 1, HistoryDay.Tomorrow, "HIIT", IntervalConfig())

    /** The followed program's next day with no plan of its own: due every day. */
    private val unplanned = UpNextItem.Lift(TODAY, HistoryDay.Today, programDay, minutes = 15)
    private val plannedLift = UpNextItem.Lift(TODAY, HistoryDay.Today, programDay, planId = 5, minutes = 15)

    @Test
    fun `with nothing up next nothing leads`() {
        val agenda = todayAgenda(emptyList())
        assertNull(agenda.lead)
        assertEquals(emptyList(), agenda.also)
        assertEquals(emptyList(), agenda.later)
    }

    @Test
    fun `on a day with no sport the first leads and the rest come after`() {
        val agenda = todayAgenda(listOf<UpNextItem>(unplanned, tabata, tomorrow))
        assertEquals(unplanned, agenda.lead)
        assertEquals(emptyList(), agenda.also)
        assertEquals(listOf<UpNextItem>(tabata, tomorrow), agenda.later)
    }

    @Test
    fun `on a class day the class leads and the program's day stands beside it`() {
        val agenda = todayAgenda(listOf<UpNextItem>(unplanned, bjj, tomorrow))
        assertEquals(bjj, agenda.lead)
        assertEquals(listOf<UpNextItem>(unplanned), agenda.also)
        assertEquals(listOf<UpNextItem>(tomorrow), agenda.later)
        assertEquals(listOf<UpNextItem>(bjj, unplanned, tomorrow), agenda.all)
    }

    @Test
    fun `a workout planned on a class day leads and the class is beside it`() {
        val agenda = todayAgenda(listOf<UpNextItem>(unplanned, tabata, bjj, tomorrow))
        assertEquals(tabata, agenda.lead)
        assertEquals(listOf<UpNextItem>(bjj, unplanned), agenda.also)
        assertEquals(listOf<UpNextItem>(tomorrow), agenda.later)
    }

    @Test
    fun `a session on Lift planned for a class day leads as a workout does`() {
        val agenda = todayAgenda(listOf<UpNextItem>(plannedLift, bjj))
        assertEquals(plannedLift, agenda.lead)
        assertEquals(listOf<UpNextItem>(bjj), agenda.also)
    }

    @Test
    fun `two sports on a day lead in the order planned`() {
        val agenda = todayAgenda(listOf<UpNextItem>(bjj, climbing))
        assertEquals(bjj, agenda.lead)
        assertEquals(listOf<UpNextItem>(climbing), agenda.also)
    }

    @Test
    fun `a second workout planned for a class day comes after the lead`() {
        val second = tabata.copy(planId = 6, name = "Hangboard")
        val agenda = todayAgenda(listOf<UpNextItem>(tabata, second, bjj))
        assertEquals(tabata, agenda.lead)
        assertEquals(listOf<UpNextItem>(bjj), agenda.also)
        assertEquals(listOf<UpNextItem>(second), agenda.later)
    }

    @Test
    fun `a session under way leads even on a class day`() {
        val underWay = unplanned.copy(resume = true, minutes = null)
        val agenda = todayAgenda(listOf<UpNextItem>(underWay, bjj))
        assertEquals(underWay, agenda.lead)
        assertEquals(emptyList(), agenda.also)
        assertEquals(listOf<UpNextItem>(bjj), agenda.later)
    }

    @Test
    fun `a class on another day changes nothing today`() {
        val friday = UpNextItem.SportDay(7, TODAY + 1, HistoryDay.Tomorrow, Sport.Bjj)
        val agenda = todayAgenda(listOf<UpNextItem>(unplanned, friday))
        assertEquals(unplanned, agenda.lead)
        assertEquals(emptyList(), agenda.also)
        assertEquals(listOf<UpNextItem>(friday), agenda.later)
    }
}
