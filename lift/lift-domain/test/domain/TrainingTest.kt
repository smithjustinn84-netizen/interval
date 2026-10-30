package dev.justinsmith.bells.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrainingTest {
    private var nextId = 1L

    private fun set(exercise: Exercise, kg: Double, reps: Int, double: Boolean = false) =
        SetLog(nextId++, exercise, kg, reps, null, nextId, double)

    private fun session(vararg sets: SetLog) = LiftSession("s", 0, 60_000, "", sets.toList())

    @Test
    fun `sessions held newest first give their sets oldest first`() {
        val first = set(BuiltInExercise.Press, 16.0, 5)
        val second = set(BuiltInExercise.Press, 20.0, 5)
        val third = set(BuiltInExercise.Press, 24.0, 3)
        val newestFirst = listOf(session(third), session(first, second))
        assertEquals(listOf(first, second, third), newestFirst.setsOldestFirst())
    }

    @Test
    fun `a record is the heaviest bell with its best reps and the most reps with any bell`() {
        val records = personalRecords(
            listOf(
                set(BuiltInExercise.Press, 16.0, 8),
                set(BuiltInExercise.Press, 20.0, 3),
                set(BuiltInExercise.Press, 20.0, 5),
                set(BuiltInExercise.Press, 12.0, 12),
            ),
        )
        assertEquals(listOf(PersonalRecord.Weighted(BuiltInExercise.Press, 20.0, 5, 12, 12.0)), records)
    }

    @Test
    fun `records follow the catalogue's order`() {
        val records =
            personalRecords(listOf(set(BuiltInExercise.GetUp, 16.0, 1), set(BuiltInExercise.TwoHandSwing, 24.0, 10)))
        assertEquals(listOf(BuiltInExercise.TwoHandSwing, BuiltInExercise.GetUp), records.map { it.exercise })
    }

    @Test
    fun `a session sets a record with a heavier bell or more reps and not by matching one`() {
        val before = listOf(set(BuiltInExercise.TwoHandSwing, 24.0, 10), set(BuiltInExercise.Press, 20.0, 5))
        val heavier = session(set(BuiltInExercise.TwoHandSwing, 28.0, 5), set(BuiltInExercise.Press, 20.0, 5))
        assertEquals(listOf(BuiltInExercise.TwoHandSwing), newRecords(before, heavier).map { it.exercise })
        val first = session(set(BuiltInExercise.Snatch, 16.0, 10))
        assertEquals(listOf(BuiltInExercise.Snatch), newRecords(before, first).map { it.exercise })
    }

    @Test
    fun `a set is a record when it beats one standing but a lift's first never is`() {
        val before = listOf(set(BuiltInExercise.TwoHandSwing, 24.0, 10))
        val sets = listOf(
            set(BuiltInExercise.TwoHandSwing, 24.0, 8).copy(id = 1),
            set(BuiltInExercise.TwoHandSwing, 28.0, 5).copy(id = 2),
            set(BuiltInExercise.TwoHandSwing, 28.0, 6).copy(id = 3),
            set(BuiltInExercise.Snatch, 16.0, 10).copy(id = 4),
            set(BuiltInExercise.Snatch, 16.0, 12).copy(id = 5),
        )
        assertEquals(setOf(2L, 3L, 5L), recordSetIds(before, sets))
    }

    @Test
    fun `a record set with two bells is judged against two-bell sets alone`() {
        val before = listOf(set(BuiltInExercise.Press, 24.0, 5))
        val sets = listOf(
            set(BuiltInExercise.Press, 16.0, 5, double = true).copy(id = 1),
            set(BuiltInExercise.Press, 20.0, 5, double = true).copy(id = 2),
        )
        assertEquals(setOf(2L), recordSetIds(before, sets))
    }

    @Test
    fun `a session's weight moved and reps add up its sets`() {
        val done = session(set(BuiltInExercise.TwoHandSwing, 24.0, 10), set(BuiltInExercise.GetUp, 20.0, 5))
        assertEquals(340.0, done.tonnageKg)
        assertEquals(15, done.reps)
        assertEquals(60, done.elapsedSeconds(nowMillis = 0))
    }

    @Test
    fun `a set with two bells moves twice the weight`() {
        assertEquals(320.0, session(set(BuiltInExercise.Press, 16.0, 10, double = true)).tonnageKg)
    }

    @Test
    fun `moved counts weight added to bodyweight but no band or mat or hold`() {
        val pushUps = SetLog(1, BuiltInExercise.PushUp, 10.0, 6, null, 0)
        val bare = SetLog(2, BuiltInExercise.PushUp, 0.0, 15, null, 0)
        val band = SetLog(3, BuiltInExercise.BandPullApart, 0.0, 20, null, 0, band = Band.of(BandLevel.Medium))
        val mat = SetLog(4, BuiltInExercise.Hundred, 0.0, 100, null, 0)
        val hold = SetLog(5, BuiltInExercise.Plank, 10.0, 0, null, 0, seconds = 60)
        assertEquals(60.0, session(pushUps, bare, band, mat, hold).tonnageKg)
    }

    @Test
    fun `every measure sets a record in its own terms`() {
        val records = personalRecords(
            listOf(
                SetLog(1, BuiltInExercise.PushUp, 0.0, 15, null, 0),
                SetLog(2, BuiltInExercise.PushUp, 10.0, 6, null, 0),
                SetLog(3, BuiltInExercise.BandPullApart, 0.0, 20, null, 0, band = Band.of(BandLevel.Light)),
                SetLog(4, BuiltInExercise.BandPullApart, 0.0, 10, null, 0, band = Band.of(BandLevel.Heavy)),
                SetLog(5, BuiltInExercise.Hundred, 0.0, 100, null, 0),
                SetLog(6, BuiltInExercise.Plank, 0.0, 0, null, 0, seconds = 45),
                SetLog(7, BuiltInExercise.Plank, 0.0, 0, null, 0, seconds = 90),
                SetLog(8, UnknownExercise("Gone", Equipment.Mat, Measure.Reps), 0.0, 10, null, 0),
            ),
        )
        assertEquals(
            setOf(
                PersonalRecord.Weighted(BuiltInExercise.PushUp, 10.0, 6, 15, 0.0),
                PersonalRecord.Banded(
                    BuiltInExercise.BandPullApart,
                    Band.of(BandLevel.Heavy),
                    10,
                    20,
                    Band.of(BandLevel.Light),
                ),
                PersonalRecord.Weighted(BuiltInExercise.Hundred, 0.0, 100, 100, 0.0),
                PersonalRecord.Held(BuiltInExercise.Plank, 90),
            ),
            records.toSet(),
        )
    }

    @Test
    fun `a longer hold or a heavier band is a new record and an equal one is not`() {
        val plank = { seconds: Int -> SetLog(nextId++, BuiltInExercise.Plank, 0.0, 0, null, 0, seconds = seconds) }
        val band = { level: BandLevel, reps: Int ->
            SetLog(nextId++, BuiltInExercise.BandPullApart, 0.0, reps, null, 0, band = Band.of(level))
        }
        val before = listOf(plank(60), band(BandLevel.Medium, 15))
        assertEquals(emptyList(), newRecords(before, session(plank(60), band(BandLevel.Medium, 15))))
        assertEquals(
            setOf(BuiltInExercise.BandPullApart, BuiltInExercise.Plank),
            newRecords(before, session(plank(75), band(BandLevel.Heavy, 5))).map { it.exercise }.toSet(),
        )
        assertEquals(
            listOf(
                PersonalRecord.Banded(
                    BuiltInExercise.BandPullApart,
                    Band.of(BandLevel.Medium),
                    20,
                    20,
                    Band.of(BandLevel.Medium),
                ),
            ),
            newRecords(before, session(band(BandLevel.Medium, 20))),
        )
    }

    @Test
    fun `records with two bells are kept apart from those with one`() {
        val records =
            personalRecords(
                listOf(set(BuiltInExercise.Press, 24.0, 5), set(BuiltInExercise.Press, 20.0, 3, double = true)),
            )
        assertEquals(
            listOf(
                PersonalRecord.Weighted(BuiltInExercise.Press, 24.0, 5, 5, 24.0),
                PersonalRecord.Weighted(BuiltInExercise.Press, 20.0, 3, 3, 20.0, true),
            ),
            records,
        )
        // A first double press is a record of its own, though the single press is heavier.
        val doubles = session(set(BuiltInExercise.Press, 16.0, 5, double = true))
        assertEquals(listOf(true), newRecords(listOf(set(BuiltInExercise.Press, 24.0, 5)), doubles).map { it.double })
    }

    @Test
    fun `a lift is doubled only if it can be`() {
        assertEquals(true, BuiltInExercise.Clean.doubled(requested = true))
        assertEquals(false, BuiltInExercise.GetUp.doubled(requested = true))
        assertEquals(true, BuiltInExercise.ArmorBuildingComplex.doubled(requested = false))
        assertEquals(false, BuiltInExercise.Press.sided(double = true))
        assertEquals(true, BuiltInExercise.Press.sided(double = false))
    }

    @Test
    fun `the built-ins keep the keys their sets were stored under`() {
        assertEquals(
            listOf(
                "TwoHandSwing", "OneHandSwing", "Clean", "Press", "Snatch", "GetUp", "GobletSquat", "Row", "Jerk",
                "LongCycle", "Halo", "FrontSquat", "ArmorBuildingComplex", "Deadlift", "HighPull", "RackedLunge",
                "Windmill", "BottomsUpPress", "IronCardio",
            ),
            kettlebellLifts.map { it.key },
        )
        BuiltInExercise.entries.forEach { assertEquals(it, Exercise.parse(it.key)) }
        assertNull(Exercise.parse("SeesawPress"))
    }

    @Test
    fun `an exercise the user added is found by its key among theirs`() {
        val plank = CustomExercise("a1", "Copenhagen plank", Equipment.Bodyweight, Measure.Time, true, Focus.Core)
        assertEquals("custom:a1", plank.key)
        assertEquals(plank, Exercise.parse("custom:a1", listOf(plank)))
        assertNull(Exercise.parse("custom:b2", listOf(plank)))
        assertEquals(Doubles.Never, plank.doubles)
    }

    @Test
    fun `the catalogue runs built-ins then the user's by name then unknowns`() {
        val zercher = CustomExercise("z", "zercher carry", Equipment.Kettlebell, Measure.Reps, false, Focus.FullBody)
        val bear = CustomExercise("b", "Bear crawl", Equipment.Bodyweight, Measure.Time, false, Focus.FullBody)
        val unknown = UnknownExercise("SeesawPress", Equipment.Kettlebell, Measure.Reps)
        assertEquals(
            listOf(BuiltInExercise.TwoHandSwing, BuiltInExercise.Seal, bear, zercher, unknown),
            listOf(unknown, zercher, BuiltInExercise.Seal, bear, BuiltInExercise.TwoHandSwing)
                .sortedWith(Exercise.order),
        )
        assertEquals(Doubles.Optional, zercher.doubles)
    }

    @Test
    fun `the starter catalogue has 94 exercises across five kinds of equipment`() {
        assertEquals(
            mapOf(
                Equipment.Kettlebell to 19,
                Equipment.Barbell to 20,
                Equipment.Bodyweight to 22,
                Equipment.Band to 15,
                Equipment.Mat to 18,
            ),
            BuiltInExercise.entries.groupingBy { it.equipment }.eachCount(),
        )
        assertEquals(BuiltInExercise.entries.take(19), kettlebellLifts)
        assertEquals(BuiltInExercise.entries.drop(19).take(20), barbellLifts)
    }

    @Test
    fun `every barbell lift is prefixed so no key clashes with a bell lift`() {
        barbellLifts.forEach {
            assertTrue(it.key.startsWith("Barbell"), it.key)
            assertEquals(false, it.perSide, it.key)
        }
    }

    @Test
    fun `a barbell set moves the bar as loaded times the reps`() {
        val set = SetLog(1, BuiltInExercise.BarbellBackSquat, 62.5, 5, null, 0)
        assertEquals(312.5, set.tonnageKg)
        assertTrue(Equipment.Barbell.takesKg)
    }

    @Test
    fun `only kettlebell lifts can be done with two bells`() {
        BuiltInExercise.entries.filter { it.equipment != Equipment.Kettlebell }.forEach {
            assertEquals(Doubles.Never, it.doubles, it.key)
        }
    }

    @Test
    fun `holds are the exercises measured in time`() {
        assertEquals(
            listOf(
                BuiltInExercise.WallSit,
                BuiltInExercise.Plank,
                BuiltInExercise.SidePlank,
                BuiltInExercise.HollowHold,
                BuiltInExercise.Swimming,
            ),
            BuiltInExercise.entries.filter { it.measure == Measure.Time },
        )
    }

    @Test
    fun `weights are written without needless decimals`() {
        assertEquals("24", formatKg(24.0))
        assertEquals("1.5", formatKg(1.5))
        assertEquals("2400", formatKg(2400.0))
        assertEquals("1.25", formatKg(1.25))
        assertEquals("61.25", formatKg(61.25))
        assertEquals("0.05", formatKg(0.05))
    }
}
