package dev.justinsmith.bells.data

import dev.justinsmith.bells.domain.Band
import dev.justinsmith.bells.domain.BandColour
import dev.justinsmith.bells.domain.BandLevel
import dev.justinsmith.bells.domain.Block
import dev.justinsmith.bells.domain.BlockKind
import dev.justinsmith.bells.domain.BuiltInExercise
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.ExerciseDraft
import dev.justinsmith.bells.domain.Focus
import dev.justinsmith.bells.domain.Measure
import dev.justinsmith.bells.domain.ProgramDraft
import dev.justinsmith.bells.domain.ProgramId
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.domain.SaveScope
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.domain.UnknownExercise
import dev.justinsmith.bells.domain.planChanges
import dev.justinsmith.bells.domain.program
import dev.justinsmith.bells.fakes.FakeBandDao
import dev.justinsmith.bells.fakes.FakeBellDao
import dev.justinsmith.bells.fakes.FakeCustomExerciseDao
import dev.justinsmith.bells.fakes.FakeCustomProgramDao
import dev.justinsmith.bells.fakes.FakeFollowingDao
import dev.justinsmith.bells.fakes.FakeSessionDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BellsRepositoryTest {
    private var now = 1_000L
    private val sessionDao = FakeSessionDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val followingDao = FakeFollowingDao()
    private val programDao = FakeCustomProgramDao()
    private val exerciseDao = FakeCustomExerciseDao()
    private val bandDao = FakeBandDao()
    private val repository =
        BellsRepository(sessionDao, FakeBellDao(), followingDao, programDao, exerciseDao, bandDao, scope) { now }

    private val pressAndSwings = ProgramDraft(
        name = "Press and swings",
        days = listOf(
            listOf(Block(BuiltInExercise.Press, rounds = 2, kind = BlockKind.Ladder, rungs = listOf(1, 2))),
            listOf(Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 20)),
        ),
        daysPerWeek = 2,
        weeks = 3,
    )

    private fun finishDayOf(key: ProgramKey) {
        repository.startSession(key)
        repository.logSet(BuiltInExercise.Press, 16.0, 1, Side.Left)
        repository.finishSession("Press")
    }

    private fun swingsAndGetUps() {
        repository.startSession()
        repository.logSet(BuiltInExercise.TwoHandSwing, 24.0, 10, Side.Left)
        now += 60_000
        repository.logSet(BuiltInExercise.GetUp, 20.0, 5, Side.Right)
        now += 60_000
        repository.finishSession("Swings and get-ups")
    }

    @Test
    fun `a session logs sets and finishes under its title`() = runTest {
        repository.startSession()
        assertNotNull(repository.active.value)
        repository.logSet(BuiltInExercise.TwoHandSwing, 24.0, 10, Side.Left)
        val set = repository.active.value?.sets?.single()
        assertEquals(BuiltInExercise.TwoHandSwing, set?.exercise)
        // A two-sided lift keeps no side.
        assertNull(set?.side)
        now += 600_000
        repository.finishSession("Swings")
        assertNull(repository.active.value)
        val done = repository.sessions.value.single()
        assertEquals("Swings", done.title)
        assertEquals(600, done.elapsedSeconds(0))
    }

    @Test
    fun `a set with two bells keeps no side and one that can't be doubled is single`() = runTest {
        repository.startSession()
        repository.logSet(BuiltInExercise.Press, 16.0, 5, Side.Left, double = true)
        repository.logSet(BuiltInExercise.GetUp, 16.0, 1, Side.Left, double = true)
        val (press, getUp) = assertNotNull(repository.active.value).sets
        assertTrue(press.double)
        assertNull(press.side)
        assertEquals(false, getUp.double)
        assertEquals(Side.Left, getUp.side)
    }

    @Test
    fun `a stored Armor Building Complex set reads as two bells`() = runTest {
        repository.startSession()
        repository.logSet(BuiltInExercise.ArmorBuildingComplex, 24.0, 1, null)
        assertTrue(assertNotNull(repository.active.value).sets.single().double)
    }

    @Test
    fun `a held set keeps its seconds and a band set its band`() = runTest {
        repository.startSession()
        repository.logSet(BuiltInExercise.SidePlank, 0.0, 0, Side.Left, seconds = 45)
        repository.logSet(BuiltInExercise.BandPullApart, 0.0, 20, null, band = Band.of(BandLevel.Medium))
        val (plank, pullApart) = assertNotNull(repository.active.value).sets
        assertEquals(45, plank.seconds)
        assertEquals(Side.Left, plank.side)
        assertEquals(Equipment.Bodyweight, plank.equipment)
        assertEquals(Band.of(BandLevel.Medium), pullApart.band)
        assertEquals(Equipment.Band, pullApart.equipment)
    }

    @Test
    fun `seconds and a band are dropped from an exercise that takes neither`() = runTest {
        repository.startSession()
        repository.logSet(BuiltInExercise.PushUp, 0.0, 15, null, seconds = 30, band = Band.of(BandLevel.Heavy))
        val pushUp = assertNotNull(repository.active.value).sets.single()
        assertNull(pushUp.seconds)
        assertNull(pushUp.band)
    }

    @Test
    fun `an exercise the user adds is listed trimmed and its sets read with it`() = runTest {
        val uid = repository.saveExercise(
            null,
            ExerciseDraft(" Copenhagen plank ", Equipment.Bodyweight, Measure.Time, perSide = true, Focus.Core),
        )
        val added = repository.customExercises.value.single()
        assertEquals("Copenhagen plank", added.name)
        assertEquals(uid, added.uid)
        repository.startSession()
        repository.logSet(added, 0.0, 0, Side.Right, seconds = 30)
        val set = assertNotNull(repository.active.value).sets.single()
        assertEquals(added, set.exercise)
        assertEquals(30, set.seconds)
    }

    @Test
    fun `an archived exercise stays archived when changed and comes back when restored`() = runTest {
        val uid = repository.saveExercise(null, ExerciseDraft("Copenhagen plank", Equipment.Bodyweight))
        repository.setArchived(uid, archived = true)
        assertTrue(repository.customExercises.value.single().archived)
        repository.saveExercise(uid, ExerciseDraft("Copenhagen plank long lever", Equipment.Bodyweight))
        val changed = repository.customExercises.value.single()
        assertEquals("Copenhagen plank long lever", changed.name)
        assertTrue(changed.archived)
        repository.setArchived(uid, archived = false)
        assertFalse(repository.customExercises.value.single().archived)
    }

    @Test
    fun `a band renamed reads by its new name on the sets done with it`() = runTest {
        repository.startSession()
        repository.logSet(BuiltInExercise.BandPullApart, 0.0, 15, null, band = Band.of(BandLevel.Heavy))
        val heavy = repository.bands.value.single { it.id == "Heavy" }
        repository.saveBand(heavy.copy(name = " Blue loop "))
        assertEquals("Blue loop", assertNotNull(repository.active.value).sets.single().band?.name)
        assertEquals("Blue loop", repository.bands.value.single { it.id == "Heavy" }.name)
    }

    @Test
    fun `a band with sets or one started with is archived and one added unused is deleted`() = runTest {
        val travel = repository.addBand("Travel band", BandColour.Purple)
        val added = repository.bands.value.single { it.id == travel }
        assertEquals(5, added.position)
        repository.removeBand(added)
        assertTrue(repository.bands.value.none { it.id == travel })
        repository.removeBand(repository.bands.value.first())
        assertTrue(repository.bands.value.first().archived)
        val loop = repository.addBand("Loop", BandColour.Red)
        val kept = repository.bands.value.single { it.id == loop }
        repository.startSession()
        repository.logSet(BuiltInExercise.BandPullApart, 0.0, 15, null, band = kept)
        repository.removeBand(kept)
        assertTrue(repository.bands.value.single { it.id == kept.id }.archived)
    }

    @Test
    fun `a set this build cannot name is kept as an unknown exercise`() = runTest {
        repository.startSession()
        val session = assertNotNull(repository.active.value).uid
        sessionDao.sets.value = listOf(
            SetEntity(0, session, "SeesawPress", 60.0, 5, null, 0, equipment = "Kettlebell"),
            SetEntity(0, session, "custom:gone", 0.0, 0, null, 1, equipment = "Mat", seconds = 40),
        )
        val (seesaw, gone) = assertNotNull(repository.active.value).sets
        assertEquals(UnknownExercise("SeesawPress", Equipment.Kettlebell, Measure.Reps), seesaw.exercise)
        assertEquals(300.0, seesaw.tonnageKg)
        assertEquals(UnknownExercise("custom:gone", Equipment.Mat, Measure.Time), gone.exercise)
    }

    @Test
    fun `a set logged before version 7 was done with a kettlebell`() = runTest {
        repository.startSession()
        val session = assertNotNull(repository.active.value).uid
        sessionDao.sets.value = listOf(SetEntity(0, session, "Press", 16.0, 5, "Left", 0))
        val press = assertNotNull(repository.active.value).sets.single()
        assertEquals(BuiltInExercise.Press, press.exercise)
        assertEquals(Equipment.Kettlebell, press.equipment)
        assertNull(press.seconds)
    }

    @Test
    fun `a program made with two bells keeps them`() = runTest {
        val draft = pressAndSwings.changeBlock(0, 0) { it.copy(double = true) }
        repository.saveProgram(null, draft)
        assertEquals(true, repository.customPrograms.value.single().days[0][0].double)
    }

    @Test
    fun `a session finished with no sets is dropped`() = runTest {
        repository.startSession()
        repository.finishSession("")
        assertTrue(repository.sessions.value.isEmpty())
        assertTrue(sessionDao.sessions.value.isEmpty())
    }

    @Test
    fun `a deleted session leaves History and stays as a tombstone`() = runTest {
        swingsAndGetUps()
        val uid = repository.sessions.value.single().uid
        now += 1_000
        repository.deleteSession(uid)
        assertTrue(repository.sessions.value.isEmpty())
        val row = sessionDao.sessions.value.single()
        assertTrue(row.deleted)
        assertEquals(now, row.updatedAtEpochMillis)
    }

    @Test
    fun `following a program counts its days by the sessions of it finished`() = runTest {
        assertNull(repository.programDay.value)
        val ladders = ProgramKey.BuiltIn(ProgramId.PressLadders)
        repository.follow(ladders)
        assertEquals(program(ProgramId.PressLadders), repository.following.value)
        assertEquals("PressLadders", followingDao.row.value?.programId)
        assertEquals(1, repository.programDay.value?.number)
        // A free session is not a day of it.
        swingsAndGetUps()
        assertEquals(1, repository.programDay.value?.number)
        repository.startSession(ladders)
        assertEquals(ladders, repository.active.value?.program)
        repository.logSet(BuiltInExercise.Press, 16.0, 1, Side.Left)
        repository.finishSession("Press")
        assertEquals(2, repository.programDay.value?.number)
        repository.stopFollowing()
        assertNull(repository.programDay.value)
    }

    @Test
    fun `a program made is saved and followed like a built-in`() = runTest {
        val uid = repository.saveProgram(null, pressAndSwings.copy(name = "  Press and swings "))
        val made = repository.customPrograms.value.single()
        assertEquals(ProgramKey.Custom(uid), made.key)
        assertEquals("Press and swings", made.name)
        assertEquals(6, made.totalDays)
        repository.follow(made.key)
        assertEquals(made, repository.following.value)
        assertEquals(BlockKind.Ladder, repository.programDay.value?.blocks?.single()?.kind)
        finishDayOf(made.key)
        assertEquals(2, repository.programDay.value?.number)
        assertEquals(BuiltInExercise.TwoHandSwing, repository.programDay.value?.blocks?.single()?.exercise)
    }

    @Test
    fun `changing a program followed keeps the days done`() = runTest {
        val uid = repository.saveProgram(null, pressAndSwings)
        repository.follow(ProgramKey.Custom(uid))
        finishDayOf(ProgramKey.Custom(uid))
        repository.saveProgram(uid, pressAndSwings.copy(name = "Renamed", weeks = 1))
        assertEquals("Renamed", repository.following.value?.name)
        assertEquals(2, repository.programDay.value?.number)
        assertEquals(1, repository.customPrograms.value.size)
    }

    @Test
    fun `deleting the program followed stops following it`() = runTest {
        val uid = repository.saveProgram(null, pressAndSwings)
        val other = repository.saveProgram(null, pressAndSwings.copy(name = "Other"))
        repository.follow(ProgramKey.Custom(uid))
        repository.deleteProgram(other)
        assertEquals(ProgramKey.Custom(uid), repository.following.value?.key)
        repository.deleteProgram(uid)
        assertNull(repository.following.value)
        assertNull(repository.programDay.value)
        assertTrue(repository.customPrograms.value.isEmpty())
    }

    @Test
    fun `a stored program of only unknown lifts is left out`() = runTest {
        val days = """[[{"exercise":"SeesawPress","kind":"Sets","rounds":3,"reps":5}]]"""
        programDao.rows.value = listOf(CustomProgramEntity("abc", "Future", 3, 4, days, 0))
        followingDao.row.value = FollowingEntity(programId = "custom:abc", startedAtEpochMillis = 0)
        assertTrue(repository.customPrograms.value.isEmpty())
        assertNull(repository.following.value)
    }

    @Test
    fun `a stored block of an unknown lift drops only that block`() = runTest {
        val days = """[[{"exercise":"SeesawPress","kind":"Sets","rounds":3,"reps":5}],""" +
            """[{"exercise":"Press","kind":"Sets","rounds":3,"reps":5,"extra":true}]]"""
        programDao.rows.value = listOf(CustomProgramEntity("abc", "Mixed", 3, 4, days, 0))
        val made = repository.customPrograms.value.single()
        assertEquals(listOf(listOf(Block(BuiltInExercise.Press, rounds = 3, reps = 5))), made.days)
    }

    @Test
    fun `a session plan changed is kept with the session`() = runTest {
        repository.follow(ProgramKey.BuiltIn(ProgramId.GobletSquatRow))
        repository.startSession(ProgramKey.BuiltIn(ProgramId.GobletSquatRow))
        val plan = repository.programDay.value!!.today.change(0) { it.copy(kg = 28.0) }
        repository.setSessionPlan(plan)
        assertEquals(plan, repository.active.value?.plan)
        assertEquals(
            28.0,
            sessionDao.sessions.value.single().plan?.let {
                decodePlan(it, emptyList())
            }?.blocks?.first()?.block?.kg,
        )
    }

    @Test
    fun `changes to a built-in go in a copy followed from the day after`() = runTest {
        val key = ProgramKey.BuiltIn(ProgramId.GobletSquatRow)
        repository.follow(key)
        finishDayOf(key)
        val day = repository.programDay.value!!.copy(number = 1)
        val changes = planChanges(day.copy(plan = day.today.change(0) { it.copy(kg = 28.0) }))!!
        val copy = repository.saveChanges(changes, SaveScope.ThisDay, "Goblet squat and row (yours)")
        assertTrue(copy is ProgramKey.Custom)
        assertEquals(copy, repository.following.value?.key)
        assertEquals(2, repository.programDay.value?.number)
        assertEquals(28.0, repository.following.value?.days?.single()?.first()?.kg)
        assertEquals("Goblet squat and row (yours)", repository.following.value?.name)
    }

    @Test
    fun `changes to a program made are kept in it and its place stays`() = runTest {
        val uid = repository.saveProgram(null, pressAndSwings)
        val key = ProgramKey.Custom(uid)
        repository.follow(key)
        finishDayOf(key)
        val program = repository.customPrograms.value.single()
        val day = program.dayAfter(0)!!
        val changes = planChanges(day.copy(plan = day.today.change(0) { it.copy(rounds = 3) }))!!
        assertEquals(key, repository.saveChanges(changes, SaveScope.ThisDay, "unused"))
        assertEquals(1, repository.customPrograms.value.size)
        assertEquals(3, repository.customPrograms.value.single().days.first().single().rounds)
        assertEquals(2, repository.programDay.value?.number)
    }

    @Test
    fun `changes on the last day of a built-in are kept in a copy not followed`() = runTest {
        val key = ProgramKey.BuiltIn(ProgramId.GobletSquatRow)
        repository.follow(key)
        val last = program(ProgramId.GobletSquatRow).let { it.dayAfter(it.totalDays - 1)!! }
        val changes = planChanges(last.copy(plan = last.today.change(1) { it.copy(reps = 12) }))!!
        assertFalse(changes.goesOn)
        repository.saveChanges(changes, SaveScope.ThisDay, "Mine")
        assertEquals(key, repository.following.value?.key)
        assertEquals("Mine", repository.customPrograms.value.single().name)
    }
}
