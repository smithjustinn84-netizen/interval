package dev.justinsmith.interval.export

import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LoggedSport
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.BandColour
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.Side
import kotlin.test.Test
import kotlin.test.assertEquals

class SpreadsheetTest {
    private val minute = 60_000L
    private val day = 24 * 60 * minute

    private object Words : SheetWords {
        override val header = listOf(
            "Date", "Time", "Kind", "Name", "Exercise", "Side", "Weight", "Unit", "Reps",
            "Seconds", "Warm-up", "Minutes", "Effort", "Note", "Average bpm", "Peak bpm",
        )
        override val workout = "Workout"
        override val sportDay = "Sport"
        override val session = "Lift session"
        override val set = "Set"
        override val warmUp = "Yes"
        override fun side(side: Side) = side.name
        override fun sport(sport: Sport) = (sport as? Sport.Custom)?.name ?: sport.toString()
        override fun lift(exercise: Exercise, double: Boolean) = exercise.key + if (double) " x2" else ""
        override fun shape(config: IntervalConfig) = "${config.rounds} rounds"
        override fun unit(unit: WeightUnit) = unit.name.lowercase()
    }

    private val tabata = IntervalConfig(workSeconds = 20, restSeconds = 10, rounds = 8)

    private fun rows(
        history: List<HistoryEntry> = emptyList(),
        sports: List<LoggedSport> = emptyList(),
        sessions: List<LiftSession> = emptyList(),
        unit: WeightUnit = WeightUnit.Kg,
        names: Map<IntervalConfig, String> = emptyMap(),
    ) = sheetRows(history, sports, sessions, { names[it] }, unit, Words, FixedTimestampFormatter)

    @Test
    fun `a workout is a row with its name or its shape and how it went`() {
        val finished = 3 * day + 9 * 60 * minute
        val entry =
            HistoryEntry(finished, tabata, elapsedSeconds = 240, endedEarly = false, effort = PerceivedEffort.Hard)
        val row = rows(listOf(entry), names = mapOf(tabata to "Tabata")).single()
        assertEquals(listOf("1970-01-04", "08:56", "Workout", "Tabata"), row.cells.take(4))
        assertEquals("4", row.minutes)
        assertEquals("3", row.effort)
        assertEquals("8 rounds", rows(listOf(entry)).single().name)
    }

    @Test
    fun `a session leads its sets each with its lift side weight and reps`() {
        val start = 2 * day + 18 * 60 * minute
        val sets = listOf(
            SetLog.of(2, BuiltInExercise.Press, 16.0, 5, Side.Right, start + 3 * minute),
            SetLog.of(1, BuiltInExercise.Press, 16.0, 5, Side.Left, start + 2 * minute),
            SetLog.of(3, BuiltInExercise.TwoHandSwing, 24.0, 10, null, start + minute).copy(warmUp = true),
        )
        val session = LiftSession("s", start, start + 30 * minute, "Press day", sets)
        val rows = rows(sessions = listOf(session))
        assertEquals(listOf("Lift session", "Set", "Set", "Set"), rows.map { it.kind })
        assertEquals("30", rows[0].minutes)
        assertEquals(listOf("TwoHandSwing", "Press", "Press"), rows.drop(1).map { it.exercise })
        assertEquals(listOf("", "Left", "Right"), rows.drop(1).map { it.side })
        assertEquals("Yes", rows[1].warmUp)
        assertEquals(listOf("24", "kg", "10"), listOf(rows[1].weight, rows[1].unit, rows[1].reps))
    }

    @Test
    fun `weights are in the app's unit a bell by its pound size`() {
        val set = SetLog.of(1, BuiltInExercise.Press, 16.0, 5, Side.Left, minute)
        val row = rows(sessions = listOf(LiftSession("s", 0, 10 * minute, "P", listOf(set))), unit = WeightUnit.Lb)[1]
        assertEquals("35", row.weight)
        assertEquals("lb", row.unit)
    }

    @Test
    fun `a band set says the band and no unit`() {
        val band = Band("loop", "Door loop", BandColour.Purple, 0)
        val set = SetLog.of(1, BuiltInExercise.BandPullApart, 0.0, 15, null, minute, band = band)
        val row = rows(sessions = listOf(LiftSession("s", 0, 10 * minute, "Bands", listOf(set))))[1]
        assertEquals("Door loop", row.weight)
        assertEquals("", row.unit)
    }

    @Test
    fun `a sport day has its date and no time and everything is oldest first`() {
        val sport = LoggedSport(1, Sport.Custom("Judo"), epochDay = 5, loggedAtEpochMillis = 9 * day)
        val entry = HistoryEntry(2 * day, tabata, 240, endedEarly = false)
        val rows = rows(listOf(entry), listOf(sport))
        assertEquals(listOf("Workout", "Sport"), rows.map { it.kind })
        assertEquals(listOf("1970-01-06", "", "Sport", "Judo"), rows[1].cells.take(4))
    }

    @Test
    fun `a session still under way is left out`() {
        assertEquals(emptyList(), rows(sessions = listOf(LiftSession("s", 0, null, "Now", emptyList()))))
    }

    @Test
    fun `a cell with a comma a quote or a line is quoted`() {
        val entry = HistoryEntry(day, tabata, 240, endedEarly = false, note = "Hard, but \"good\"\nagain")
        val csv = csvOf(listOf("Note"), rows(listOf(entry)))
        assertEquals(true, csv.contains("\"Hard, but \"\"good\"\"\nagain\""))
        assertEquals(true, csv.startsWith("Note\r\n"))
    }

    @Test
    fun `the file is named for its day`() {
        assertEquals("interval-log-1970-01-11.csv", sheetFileName(10))
    }
}
