package dev.justinsmith.interval.export

import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.fakes.FakeAppInfo
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.TODAY
import dev.justinsmith.interval.fakes.noonOf
import dev.justinsmith.interval.fixtures.DAMAGED_EXPORT
import dev.justinsmith.interval.fixtures.FOREIGN_JSON
import dev.justinsmith.interval.fixtures.SCHEMA_1_EXPORT
import dev.justinsmith.interval.fixtures.SCHEMA_5_EXPORT
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.ThemeMode
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.platform.ShareableFile
import dev.justinsmith.interval.settings.ImportCounts
import dev.justinsmith.interval.settings.ImportReview
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.lift.data.LiftRepository
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.ExerciseDraft
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.fakes.logSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A phone's two repositories over fakes, and its export and import. */
private class Phone(val now: Long) {
    val intervals: IntervalRepository = FakeStorage().repository(now = { now })
    val lift: LiftRepository = FakeLiftStorage().repository(now = { now })
    val exporter = AppDataExporter(intervals, lift, FakeAppInfo, { now }, FixedTimestampFormatter)
    val importer = AppDataImporter(intervals, lift, Dispatchers.Unconfined)

    /** What a phone holds, without what only that phone gave it: ids, uids it made, when things changed. */
    fun contents() = listOf(
        intervals.history.value.map { it.copy(id = null) }.sortedBy { it.finishedAtEpochMillis },
        intervals.customPresets.value.map { it.copy(id = null) },
        intervals.planned.value.map {
            it.copy(
                id = 0,
                workout =
                (it.workout as? PlanWorkout.Interval)?.copy(presetId = null) ?: it.workout,
            )
        },
        lift.sessions.sessions.value.map { session -> session.copy(sets = session.sets.map { it.copy(id = 0) }) },
        lift.exercises.customExercises.value,
        lift.programs.customPrograms.value,
        lift.gear.bands.value,
        lift.gear.bells.value,
        lift.gear.bar.value,
    )
}

class AppDataImporterTest {
    private val now = noonOf(TODAY)

    /** A phone with something of every kind on it. */
    private suspend fun stocked(): Phone = Phone(now).apply {
        intervals.savePreset(Preset("Hills", IntervalConfig(workSeconds = 45, activity = Activity.Running)))
        intervals.updateConfig { IntervalConfig(rounds = 4) }
        val bookended =
            IntervalConfig(
                rounds = 4,
                warmUpSeconds = 60,
                coolDownSeconds = 30,
                sets = 2,
                setRestSeconds = 45,
                workStepSeconds = 10,
            )
        val workout = intervals.recordWorkout(bookended, elapsedSeconds = 200, workSeconds = 120)
        intervals.reflectOnWorkout(workout, PerceivedEffort.Hard, "Felt strong")
        intervals.keepHeartRate(workout, HeartRate(averageBpm = 141, peakBpm = 172))
        intervals.planWeekly(
            PlanWorkout.Interval(
                "Hills",
                IntervalConfig(workSeconds = 45, warmUpSeconds = 300, activity = Activity.Running),
            ),
            setOf(2),
        )
        intervals.planOnce(PlanWorkout.Lift.AnySession, TODAY + 1)
        intervals.updateAppSettings { it.copy(theme = ThemeMode.Light, weeklyGoal = 3) }
        lift.gear.setOwned(20.0, owned = true)
        lift.gear.setBar(15.0)
        val press = lift.exercises.saveExercise(null, ExerciseDraft("Landmine press", Equipment.Barbell))
        lift.sessions.startSession(null)
        lift.sets.logSet(BuiltInExercise.TwoHandSwing, bellKg = 16.0, reps = 10, side = null)
        lift.sets.logSet(
            lift.exercises.customExercises.value.single {
                it.uid == press
            },
            bellKg = 30.0,
            reps = 8,
            side = null,
        )
        lift.sessions.finishSession("Swings and press")
        lift.sessions.keepHeartRate(
            lift.sessions.sessions.value.single().uid,
            HeartRate(averageBpm = 118, peakBpm = 149),
        )
    }

    /** The phone's export, as Settings' Export writes it. */
    private suspend fun Phone.file(): ShareableFile = exporter.export()

    @Test
    fun `an export imported on an empty phone gives the same data`() = runTest {
        val source = stocked()
        val target = Phone(now + 1)

        val review = assertIs<ImportReview.Ready>(target.importer.review(source.file()))
        assertEquals(ImportCounts(workouts = 1, presets = 1, plans = 2, sessions = 1, exercises = 1), review.new)
        assertEquals(0, review.alreadyHere)
        assertEquals(review.new, target.importer.import(source.file(), withSettings = false))

        assertEquals(source.contents(), target.contents())
        // Settings only when asked for.
        assertEquals(ThemeMode.Dark, target.intervals.appSettings.value.theme)
    }

    @Test
    fun `a phone's own export adds nothing`() = runTest {
        val phone = stocked()
        val before = phone.contents()

        val review = assertIs<ImportReview.Ready>(phone.importer.review(phone.file()))
        assertEquals(ImportCounts(), review.new)
        assertEquals(6, review.alreadyHere)
        phone.importer.import(phone.file(), withSettings = false)
        assertEquals(before, phone.contents())
    }

    @Test
    fun `importing twice adds once`() = runTest {
        val source = stocked()
        val target = Phone(now)
        target.importer.import(source.file(), withSettings = false)
        assertEquals(ImportCounts(), target.importer.import(source.file(), withSettings = false))
        assertEquals(source.contents(), target.contents())
    }

    @Test
    fun `also settings brings the file's settings with it`() = runTest {
        val target = Phone(now)
        target.importer.import(stocked().file(), withSettings = true)
        assertEquals(ThemeMode.Light, target.intervals.appSettings.value.theme)
        assertEquals(3, target.intervals.appSettings.value.weeklyGoal)
        assertEquals(4, target.intervals.config.value.rounds)
    }

    @Test
    fun `a schema 1 file from 0 point 2 reads`() = runTest {
        val target = Phone(now)
        val file = ShareableFile("old.json", ExportBundle.MIME_TYPE, SCHEMA_1_EXPORT)

        val review = assertIs<ImportReview.Ready>(target.importer.review(file))
        assertEquals("0.2.0", review.version)
        assertEquals(ImportCounts(workouts = 2, presets = 1, plans = 1, sessions = 1), review.new)
        target.importer.import(file, withSettings = true)

        val history = target.intervals.history.value
        assertEquals(listOf(490, 130), history.map { it.elapsedSeconds })
        assertNull(history.first().effort)
        assertEquals(Activity.Running, history.first().config.activity)
        // From before sets: every shape is one set.
        assertTrue(
            history.all {
                it.config.sets == 1 && it.config.setRestSeconds == 0 && it.config.workStepSeconds == 0
            },
        )
        val plan = target.intervals.planned.value.single().workout as PlanWorkout.Interval
        assertEquals("Hills", plan.name)
        // Linked to the preset as it landed here, not to the other phone's id.
        assertEquals(target.intervals.customPresets.value.single().id, plan.presetId)
        assertEquals(listOf(16.0, 24.0), target.lift.gear.bells.value)
        assertEquals(120, target.lift.sets.restSeconds.value)
        assertEquals(10, target.lift.sessions.sessions.value.single().sets.single().reps)
        assertEquals(82.0, target.intervals.appSettings.value.body.weightKg)
    }

    @Test
    fun `a newer file says so and changes nothing`() = runTest {
        val target = Phone(now)
        val file = ShareableFile("new.json", ExportBundle.MIME_TYPE, SCHEMA_5_EXPORT)
        assertEquals(ImportReview.TooNew("0.9.0"), target.importer.review(file))
        assertFailsWith<IllegalArgumentException> { target.importer.import(file, withSettings = true) }
        assertTrue(target.intervals.history.value.isEmpty())
    }

    @Test
    fun `a damaged or foreign file is refused`() = runTest {
        val target = Phone(now)
        for (content in listOf(DAMAGED_EXPORT, FOREIGN_JSON, "", "[]")) {
            assertEquals(
                ImportReview.Unreadable,
                target.importer.review(ShareableFile("x.json", "application/json", content)),
            )
        }
    }

    @Test
    fun `a value out of the app's ranges makes the file unreadable`() = runTest {
        val target = Phone(now)
        val content = SCHEMA_1_EXPORT.replace("\"rounds\": 6", "\"rounds\": 600")
        assertEquals(
            ImportReview.Unreadable,
            target.importer.review(ShareableFile("x.json", "application/json", content)),
        )
    }

    @Test
    fun `a heart rate no heart beats at makes the file unreadable`() = runTest {
        val source = stocked()
        val file = source.file().content
        val content = file.replace("\"peakBpm\": 172", "\"peakBpm\": 900")
        assertTrue(content != file)
        assertEquals(
            ImportReview.Unreadable,
            Phone(now).importer.review(ShareableFile("x.json", "application/json", content)),
        )
    }
}
