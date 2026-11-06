package dev.justinsmith.interval.export

import dev.justinsmith.interval.fakes.FakeAppInfo
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.TODAY
import dev.justinsmith.interval.fakes.noonOf
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.model.ThemeMode
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.BandLevel
import dev.justinsmith.lift.domain.Block
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.ProgramDraft
import dev.justinsmith.lift.domain.withLoad
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.fakes.logSet
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import dev.justinsmith.lift.domain.PerceivedEffort as LiftEffort

class ExportTest {
    private val now = noonOf(TODAY)
    private val storage = FakeStorage()
    private val lift = FakeLiftStorage()
    private val intervals = storage.repository(now = { now })
    private val bells = lift.repository(now = { now })
    private val exporter = AppDataExporter(intervals, bells, FakeAppInfo, { now }, FixedTimestampFormatter)

    @Test
    fun `the file is named for the day and holds both sides`() = runTest {
        intervals.presets.savePreset(
            Preset("Hills", IntervalConfig(workSeconds = 45, warmUpSeconds = 300, coolDownSeconds = 120)),
        )
        val workout = intervals.history.recordWorkout(IntervalConfig(rounds = 4), elapsedSeconds = 200)
        intervals.history.reflectOnWorkout(workout, PerceivedEffort.Hard, "Felt strong")
        intervals.settings.updateAppSettings {
            it.copy(theme = ThemeMode.Light, weeklyGoal = 3, sessionMinutes = 20)
        }
        bells.sessions.startSession()
        bells.sets.logSet(BuiltInExercise.TwoHandSwing, bellKg = 16.0, reps = 10, side = null)
        bells.sessions.finishSession("Swings")
        bells.sessions.reflectOnSession(bells.sessions.sessions.value.single().uid, LiftEffort.Steady, "")

        val file = exporter.export()

        assertEquals("interval-2026-10-15.json", file.name)
        assertEquals("application/json", file.mimeType)
        val bundle = Json.decodeFromString<ExportBundle>(file.content)
        assertEquals(ExportBundle.SCHEMA, bundle.schema)
        assertEquals("1.2.3", bundle.version)
        assertEquals(now, bundle.exportedAtEpochMillis)
        assertEquals("Light", bundle.settings.theme)
        assertEquals(3, bundle.settings.weeklyGoal)
        assertEquals(20, bundle.settings.sessionMinutes)
        assertEquals(listOf("Hills"), bundle.presets.map { it.name })
        assertEquals(45, bundle.presets.single().config.workSeconds)
        assertEquals(300, bundle.presets.single().config.warmUpSeconds)
        assertEquals(120, bundle.presets.single().config.coolDownSeconds)
        assertEquals(listOf(4), bundle.history.map { it.config.rounds })
        assertEquals(3, bundle.history.single().effort)
        assertEquals("Felt strong", bundle.history.single().note)
        val session = bundle.lift.sessions.single()
        assertEquals("Swings", session.title)
        assertEquals(listOf("TwoHandSwing"), session.sets.map { it.exercise })
        assertEquals(listOf(16.0), session.sets.map { it.bellKg })
        assertEquals(2, session.effort)
        assertNull(session.note)
        assertNull(bundle.lift.following)
    }

    @Test
    fun `sport days planned and logged go in the file by name`() = runTest {
        intervals.plans.planWeekly(PlanWorkout.SportDay(Sport.Custom("Judo")), setOf(2))
        intervals.history.logSport(Sport.Bjj, epochDay = 20_744)

        val bundle = Json.decodeFromString<ExportBundle>(exporter.export().content)

        val plan = bundle.plan.single()
        assertEquals("Custom" to "Judo", plan.sport to plan.name)
        assertNull(plan.lift)
        assertNull(plan.config)
        val day = bundle.sportDays.single()
        assertEquals(Triple(20_744L, "Bjj", null), Triple(day.epochDay, day.sport, day.name))
    }

    @Test
    fun `the file names its schema and app`() = runTest {
        val json = Json.parseToJsonElement(exporter.export().content).jsonObject

        assertEquals(ExportBundle.SCHEMA, json.getValue("schema").jsonPrimitive.int)
        assertEquals(ExportBundle.APP, json.getValue("app").jsonPrimitive.content)
    }

    @Test
    fun `a session in progress is not exported`() {
        bells.sessions.startSession()
        bells.sets.logSet(BuiltInExercise.TwoHandSwing, bellKg = 24.0, reps = 5, side = null)

        assertEquals(emptyList(), exporter.bundle(now).lift.sessions)
    }

    @Test
    fun `a program block's load is written as its weight or its band's id`() {
        bells.programs.saveProgram(
            null,
            ProgramDraft(
                name = "Mine",
                days = listOf(
                    listOf(
                        Block(BuiltInExercise.Clean, rounds = 5, reps = 5, double = true).withLoad(Load.Bell(16.0)),
                        Block(BuiltInExercise.BandPullApart, rounds = 3, reps = 15)
                            .withLoad(Load.Banded(Band.of(BandLevel.Heavy))),
                        Block(BuiltInExercise.PushUp, rounds = 3, reps = 10),
                    ),
                ),
                daysPerWeek = 3,
                weeks = 4,
            ),
        )

        val blocks = exporter.bundle(now).lift.programs.single().days.single()

        assertEquals(listOf(16.0, null, null), blocks.map { it.kg })
        assertEquals(listOf(null, "Heavy", null), blocks.map { it.band })
        assertEquals(listOf(true, false, false), blocks.map { it.double })
    }

    @Test
    fun `what is written reads back the same`() {
        intervals.presets.savePreset(Preset("Hills", IntervalConfig(workSeconds = 45)))
        val bundle = exporter.bundle(now)

        assertEquals(bundle, Json.decodeFromString<ExportBundle>(Json.encodeToString(bundle)))
    }
}
