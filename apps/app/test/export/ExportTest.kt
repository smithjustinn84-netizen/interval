package dev.justinsmith.interval.export

import dev.justinsmith.interval.fakes.FakeAppInfo
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.TODAY
import dev.justinsmith.interval.fakes.noonOf
import dev.justinsmith.interval.model.ThemeMode
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExportTest {
    private val now = noonOf(TODAY)
    private val storage = FakeStorage()
    private val lift = FakeLiftStorage()
    private val intervals = storage.repository(now = { now })
    private val bells = lift.repository(now = { now })
    private val exporter = AppDataExporter(intervals, bells, FakeAppInfo, { now }, FixedTimestampFormatter)

    @Test
    fun `the file is named for the day and holds both sides`() = runTest {
        intervals.savePreset(Preset("Hills", IntervalConfig(workSeconds = 45)))
        intervals.recordWorkout(IntervalConfig(rounds = 4), elapsedSeconds = 200)
        intervals.updateAppSettings { it.copy(theme = ThemeMode.Light, weeklyGoal = 3) }
        bells.startSession()
        bells.logSet(BuiltInExercise.TwoHandSwing, bellKg = 16.0, reps = 10, side = null)
        bells.finishSession("Swings")

        val file = exporter.export()

        assertEquals("interval-2026-10-15.json", file.name)
        assertEquals("application/json", file.mimeType)
        val bundle = Json.decodeFromString<ExportBundle>(file.content)
        assertEquals(ExportBundle.SCHEMA, bundle.schema)
        assertEquals("1.2.3", bundle.version)
        assertEquals(now, bundle.exportedAtEpochMillis)
        assertEquals("Light", bundle.settings.theme)
        assertEquals(3, bundle.settings.weeklyGoal)
        assertEquals(listOf("Hills"), bundle.presets.map { it.name })
        assertEquals(45, bundle.presets.single().config.workSeconds)
        assertEquals(listOf(4), bundle.history.map { it.config.rounds })
        val session = bundle.lift.sessions.single()
        assertEquals("Swings", session.title)
        assertEquals(listOf("TwoHandSwing"), session.sets.map { it.exercise })
        assertEquals(listOf(16.0), session.sets.map { it.bellKg })
        assertNull(bundle.lift.following)
    }

    @Test
    fun `the file names its schema and app`() = runTest {
        val json = Json.parseToJsonElement(exporter.export().content).jsonObject

        assertEquals(ExportBundle.SCHEMA, json.getValue("schema").jsonPrimitive.int)
        assertEquals(ExportBundle.APP, json.getValue("app").jsonPrimitive.content)
    }

    @Test
    fun `a session in progress is not exported`() {
        bells.startSession()
        bells.logSet(BuiltInExercise.TwoHandSwing, bellKg = 24.0, reps = 5, side = null)

        assertEquals(emptyList(), exporter.bundle(now).lift.sessions)
    }

    @Test
    fun `what is written reads back the same`() {
        intervals.savePreset(Preset("Hills", IntervalConfig(workSeconds = 45)))
        val bundle = exporter.bundle(now)

        assertEquals(bundle, Json.decodeFromString<ExportBundle>(Json.encodeToString(bundle)))
    }
}
