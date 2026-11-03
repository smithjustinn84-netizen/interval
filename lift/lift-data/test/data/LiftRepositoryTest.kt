package dev.justinsmith.lift.data

import dev.justinsmith.interval.fakes.SequentialIds
import dev.justinsmith.interval.storage.StorageError
import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.BandColour
import dev.justinsmith.lift.domain.BandLevel
import dev.justinsmith.lift.domain.Block
import dev.justinsmith.lift.domain.BlockKind
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.ExerciseDraft
import dev.justinsmith.lift.domain.Focus
import dev.justinsmith.lift.domain.Followed
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.Measure
import dev.justinsmith.lift.domain.ProgramDraft
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.SaveScope
import dev.justinsmith.lift.domain.SavedTimer
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.domain.UnknownExercise
import dev.justinsmith.lift.domain.planChanges
import dev.justinsmith.lift.domain.program
import dev.justinsmith.lift.fakes.FakeBandDao
import dev.justinsmith.lift.fakes.FakeBellDao
import dev.justinsmith.lift.fakes.FakeCustomExerciseDao
import dev.justinsmith.lift.fakes.FakeCustomProgramDao
import dev.justinsmith.lift.fakes.FakeFollowingDao
import dev.justinsmith.lift.fakes.FakeLiftImportDao
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.fakes.FakeSessionDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiftRepositoryTest {
    private var now = 1_000L
    private val sessionDao = FakeSessionDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val followingDao = FakeFollowingDao()
    private val programDao = FakeCustomProgramDao(followingDao)
    private val exerciseDao = FakeCustomExerciseDao()
    private val bandDao = FakeBandDao(sessionDao)
    private val bellDao = FakeBellDao()
    private val repository = LiftRepository(
        sessionDao,
        bellDao,
        followingDao,
        programDao,
        exerciseDao,
        bandDao,
        FakeLiftImportDao(sessionDao, bellDao, followingDao, programDao, exerciseDao, bandDao),
        scope,
        clock = { now },
    )

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
        repository.sessions.startSession(key)
        repository.sets.logSet(BuiltInExercise.Press, 16.0, 1, Side.Left)
        repository.sessions.finishSession("Press")
    }

    private fun swingsAndGetUps() {
        repository.sessions.startSession()
        repository.sets.logSet(BuiltInExercise.TwoHandSwing, 24.0, 10, Side.Left)
        now += 60_000
        repository.sets.logSet(BuiltInExercise.GetUp, 20.0, 5, Side.Right)
        now += 60_000
        repository.sessions.finishSession("Swings and get-ups")
    }

    @Test
    fun `the session in progress keeps each kind of timer as it was and none once finished`() = runTest {
        val heavy = Band("mine", "Heavy", BandColour.Black, 9)
        repository.gear.saveBand(heavy)
        val timers = listOf(
            SavedTimer.Rest(500, 90),
            SavedTimer.Emom(500, BuiltInExercise.Press, 16.0, 5, 10, Side.Right, true, null, setOf(1, 3)),
            SavedTimer.Emom(500, BuiltInExercise.BandPullApart, 0.0, 12, 5, Side.Left, false, heavy, emptySet()),
            SavedTimer.Hold(500, BuiltInExercise.Plank, 0.0, 10.0, 20.0, Side.Left, false, 45, Band.Default, false),
        )
        repository.sessions.startSession()
        timers.forEach { timer ->
            repository.sets.saveTimer(timer)
            assertEquals(timer, repository.sessions.active.value?.timer)
        }
        repository.sets.saveTimer(null)
        assertNull(repository.sessions.active.value?.timer)
        repository.sets.saveTimer(timers.first())
        repository.sets.logSet(BuiltInExercise.Press, 16.0, 5, Side.Left)
        repository.sessions.finishSession("Press")
        assertNull(sessionDao.sessions.value.single().timer)
    }

    @Test
    fun `a timer that can't be read is none`() = runTest {
        repository.sessions.startSession()
        val uid = repository.sessions.active.value?.uid ?: error("No session")
        sessionDao.setTimer(uid, "not a timer")
        assertNull(repository.sessions.active.value?.timer)
        sessionDao.setTimer(uid, """{"kind":"emom","startedAt":1,"exercise":"gone","minutes":5}""")
        assertNull(repository.sessions.active.value?.timer)
    }

    @Test
    fun `a session logs sets and finishes under its title`() = runTest {
        repository.sessions.startSession()
        assertNotNull(repository.sessions.active.value)
        repository.sets.logSet(BuiltInExercise.TwoHandSwing, 24.0, 10, Side.Left)
        val set = repository.sessions.active.value?.sets?.single()
        assertEquals(BuiltInExercise.TwoHandSwing, set?.exercise)
        // A two-sided lift keeps no side.
        assertNull(set?.side)
        now += 600_000
        repository.sessions.finishSession("Swings")
        assertNull(repository.sessions.active.value)
        val done = repository.sessions.sessions.value.single()
        assertEquals("Swings", done.title)
        assertEquals(600, done.elapsedSeconds(0))
    }

    @Test
    fun `a set that can't be saved is published and the next one lands`() = runTest {
        val errors = mutableListOf<StorageError>()
        val watching = scope.launch { repository.writeErrors.collect { errors += it } }
        repository.sessions.startSession()
        sessionDao.failWrites = true
        repository.sets.logSet(BuiltInExercise.TwoHandSwing, 24.0, 10, side = null)
        sessionDao.failWrites = false
        repository.sets.logSet(BuiltInExercise.TwoHandSwing, 24.0, 12, side = null)
        assertEquals(listOf(12), repository.sessions.active.value?.sets?.map { it.reps })
        assertEquals(listOf("disk full"), errors.map { it.cause.message })
        watching.cancel()
    }

    @Test
    fun `a set removed then restored comes back with its id in its place`() = runTest {
        repository.sessions.startSession()
        repository.sets.logSet(BuiltInExercise.TwoHandSwing, 24.0, 10, Side.Left)
        now += 60_000
        repository.sets.logSet(BuiltInExercise.GetUp, 20.0, 1, Side.Left)
        now += 60_000
        repository.sets.logSet(BuiltInExercise.Press, 16.0, 5, Side.Left)
        val before = assertNotNull(repository.sessions.active.value).sets
        val middle = before[1]
        repository.sets.removeSet(middle.id)
        repository.sets.restoreSet(middle)
        assertEquals(before, repository.sessions.active.value?.sets)
    }

    @Test
    fun `a set with two bells keeps no side and one that can't be doubled is single`() = runTest {
        repository.sessions.startSession()
        repository.sets.logSet(BuiltInExercise.Press, 16.0, 5, Side.Left, double = true)
        repository.sets.logSet(BuiltInExercise.GetUp, 16.0, 1, Side.Left, double = true)
        val (press, getUp) = assertNotNull(repository.sessions.active.value).sets
        assertTrue(press.double)
        assertNull(press.side)
        assertEquals(false, getUp.double)
        assertEquals(Side.Left, getUp.side)
    }

    @Test
    fun `a stored Armor Building Complex set reads as two bells`() = runTest {
        repository.sessions.startSession()
        repository.sets.logSet(BuiltInExercise.ArmorBuildingComplex, 24.0, 1, null)
        assertTrue(assertNotNull(repository.sessions.active.value).sets.single().double)
    }

    @Test
    fun `a held set keeps its seconds and a band set its band`() = runTest {
        repository.sessions.startSession()
        repository.sets.logSet(BuiltInExercise.SidePlank, 0.0, 0, Side.Left, seconds = 45)
        repository.sets.logSet(BuiltInExercise.BandPullApart, 0.0, 20, null, band = Band.of(BandLevel.Medium))
        val (plank, pullApart) = assertNotNull(repository.sessions.active.value).sets
        assertEquals(45, plank.seconds)
        assertEquals(Side.Left, plank.side)
        assertEquals(Equipment.Bodyweight, plank.equipment)
        assertEquals(Band.of(BandLevel.Medium), pullApart.band)
        assertEquals(Equipment.Band, pullApart.equipment)
    }

    @Test
    fun `a barbell set keeps the total on the bar`() = runTest {
        repository.sessions.startSession()
        repository.sets.logSet(BuiltInExercise.BarbellPowerClean, 62.5, 3, null)
        val clean = assertNotNull(repository.sessions.active.value).sets.single()
        assertEquals(Equipment.Barbell, clean.equipment)
        assertEquals(62.5, clean.bellKg)
        assertEquals(187.5, clean.tonnageKg)
    }

    @Test
    fun `the bar is kept until it is set to none`() = runTest {
        assertNull(repository.gear.bar.value)
        repository.gear.setBar(15.0)
        assertEquals(15.0, repository.gear.bar.value)
        repository.gear.setBar(20.0)
        assertEquals(20.0, repository.gear.bar.value)
        repository.gear.setBar(null)
        assertNull(repository.gear.bar.value)
    }

    @Test
    fun `seconds and a band are dropped from an exercise that takes neither`() = runTest {
        repository.sessions.startSession()
        repository.sets.logSet(BuiltInExercise.PushUp, 0.0, 15, null, seconds = 30, band = Band.of(BandLevel.Heavy))
        val pushUp = assertNotNull(repository.sessions.active.value).sets.single()
        assertNull(pushUp.seconds)
        assertNull(pushUp.band)
    }

    @Test
    fun `an exercise the user adds is listed trimmed and its sets read with it`() = runTest {
        val uid = repository.exercises.saveExercise(
            null,
            ExerciseDraft(" Copenhagen plank ", Equipment.Bodyweight, Measure.Time, perSide = true, Focus.Core),
        )
        val added = repository.exercises.customExercises.value.single()
        assertEquals("Copenhagen plank", added.name)
        assertEquals(uid, added.uid)
        repository.sessions.startSession()
        repository.sets.logSet(added, 0.0, 0, Side.Right, seconds = 30)
        val set = assertNotNull(repository.sessions.active.value).sets.single()
        assertEquals(added, set.exercise)
        assertEquals(30, set.seconds)
    }

    @Test
    fun `an archived exercise stays archived when changed and comes back when restored`() = runTest {
        val uid = repository.exercises.saveExercise(null, ExerciseDraft("Copenhagen plank", Equipment.Bodyweight))
        repository.exercises.setArchived(uid, archived = true)
        assertTrue(repository.exercises.customExercises.value.single().archived)
        repository.exercises.saveExercise(uid, ExerciseDraft("Copenhagen plank long lever", Equipment.Bodyweight))
        val changed = repository.exercises.customExercises.value.single()
        assertEquals("Copenhagen plank long lever", changed.name)
        assertTrue(changed.archived)
        repository.exercises.setArchived(uid, archived = false)
        assertFalse(repository.exercises.customExercises.value.single().archived)
    }

    @Test
    fun `a band renamed reads by its new name on the sets done with it`() = runTest {
        repository.sessions.startSession()
        repository.sets.logSet(BuiltInExercise.BandPullApart, 0.0, 15, null, band = Band.of(BandLevel.Heavy))
        val heavy = repository.gear.bands.value.single { it.id == "Heavy" }
        repository.gear.saveBand(heavy.copy(name = " Blue loop "))
        assertEquals("Blue loop", assertNotNull(repository.sessions.active.value).sets.single().band?.name)
        assertEquals("Blue loop", repository.gear.bands.value.single { it.id == "Heavy" }.name)
    }

    @Test
    fun `a band with sets or one started with is archived and one added unused is deleted`() = runTest {
        val travel = repository.gear.addBand("Travel band", BandColour.Purple)
        val added = repository.gear.bands.value.single { it.id == travel }
        assertEquals(5, added.position)
        repository.gear.removeBand(added)
        assertTrue(repository.gear.bands.value.none { it.id == travel })
        repository.gear.removeBand(repository.gear.bands.value.first())
        assertTrue(repository.gear.bands.value.first().archived)
        val loop = repository.gear.addBand("Loop", BandColour.Red)
        val kept = repository.gear.bands.value.single { it.id == loop }
        repository.sessions.startSession()
        repository.sets.logSet(BuiltInExercise.BandPullApart, 0.0, 15, null, band = kept)
        repository.gear.removeBand(kept)
        assertTrue(repository.gear.bands.value.single { it.id == kept.id }.archived)
    }

    @Test
    fun `a set this build cannot name is kept as an unknown exercise`() = runTest {
        repository.sessions.startSession()
        val session = assertNotNull(repository.sessions.active.value).uid
        sessionDao.sets.value = listOf(
            SetEntity(0, session, "SeesawPress", 60.0, 5, null, 0, equipment = "Kettlebell"),
            SetEntity(0, session, "custom:gone", 0.0, 0, null, 1, equipment = "Mat", seconds = 40),
        )
        val (seesaw, gone) = assertNotNull(repository.sessions.active.value).sets
        assertEquals(UnknownExercise("SeesawPress", Equipment.Kettlebell, Measure.Reps), seesaw.exercise)
        assertEquals(300.0, seesaw.tonnageKg)
        assertEquals(UnknownExercise("custom:gone", Equipment.Mat, Measure.Time), gone.exercise)
    }

    @Test
    fun `a set logged before version 7 was done with a kettlebell`() = runTest {
        repository.sessions.startSession()
        val session = assertNotNull(repository.sessions.active.value).uid
        sessionDao.sets.value = listOf(SetEntity(0, session, "Press", 16.0, 5, "Left", 0))
        val press = assertNotNull(repository.sessions.active.value).sets.single()
        assertEquals(BuiltInExercise.Press, press.exercise)
        assertEquals(Equipment.Kettlebell, press.equipment)
        assertNull(press.seconds)
    }

    @Test
    fun `a program made with two bells keeps them`() = runTest {
        val draft = pressAndSwings.changeBlock(0, 0) { it.copy(double = true) }
        repository.programs.saveProgram(null, draft)
        assertEquals(true, repository.programs.customPrograms.value.single().days[0][0].double)
    }

    @Test
    fun `a session finished with no sets is dropped`() = runTest {
        repository.sessions.startSession()
        repository.sessions.finishSession("")
        assertTrue(repository.sessions.sessions.value.isEmpty())
        assertTrue(sessionDao.sessions.value.isEmpty())
    }

    @Test
    fun `a deleted session leaves History and stays as a tombstone`() = runTest {
        swingsAndGetUps()
        val uid = repository.sessions.sessions.value.single().uid
        now += 1_000
        repository.sessions.deleteSession(uid)
        assertTrue(repository.sessions.sessions.value.isEmpty())
        val row = sessionDao.sessions.value.single()
        assertTrue(row.deleted)
        assertEquals(now, row.updatedAtEpochMillis)
    }

    @Test
    fun `following a program counts its days by the sessions of it finished`() = runTest {
        assertNull(repository.programs.programDay.value)
        val ladders = ProgramKey.BuiltIn(ProgramId.PressLadders)
        repository.programs.follow(ladders)
        assertEquals(program(ProgramId.PressLadders), repository.programs.following.value)
        assertEquals("PressLadders", followingDao.row.value?.programId)
        assertEquals(1, repository.programs.programDay.value?.number)
        // A free session is not a day of it.
        swingsAndGetUps()
        assertEquals(1, repository.programs.programDay.value?.number)
        repository.sessions.startSession(ladders)
        assertEquals(ladders, repository.sessions.active.value?.program)
        // Logged as the day it is.
        assertEquals(1, repository.sessions.active.value?.day)
        repository.sets.logSet(BuiltInExercise.Press, 16.0, 1, Side.Left)
        repository.sessions.finishSession("Press")
        assertEquals(1, repository.sessions.sessions.value.first().day)
        assertEquals(2, repository.programs.programDay.value?.number)
        assertEquals(
            Followed(program(ProgramId.PressLadders), 1_000, daysBefore = 0),
            repository.programs.followed.value,
        )
        repository.programs.stopFollowing()
        assertNull(repository.programs.programDay.value)
        assertNull(repository.programs.followed.value)
    }

    @Test
    fun `deleting a day from the middle leaves the next day where it was`() = runTest {
        val ladders = ProgramKey.BuiltIn(ProgramId.PressLadders)
        repository.programs.follow(ladders)
        repeat(3) {
            finishDayOf(ladders)
            now += 60_000
        }
        assertEquals(4, repository.programs.programDay.value?.number)
        repository.sessions.deleteSession(repository.sessions.sessions.value[1].uid)
        assertEquals(listOf(3, 1), repository.sessions.sessions.value.map { it.day })
        assertEquals(4, repository.programs.programDay.value?.number)
    }

    @Test
    fun `a session logged before days were kept counts as the day after the last`() = runTest {
        val ladders = ProgramKey.BuiltIn(ProgramId.PressLadders)
        repository.programs.follow(ladders)
        finishDayOf(ladders)
        now += 60_000
        // As a build before this one left it: the day is unknown, and it follows the day before it.
        sessionDao.sessions.value += SessionEntity("old", now, now + 1, "Press", now, programId = ladders.stored)
        assertEquals(3, repository.programs.programDay.value?.number)
        now += 60_000
        finishDayOf(ladders)
        assertEquals(listOf(3, null, 1), repository.sessions.sessions.value.map { it.day })
        assertEquals(4, repository.programs.programDay.value?.number)
    }

    @Test
    fun `a session started while no program is followed has no day`() = runTest {
        val ladders = ProgramKey.BuiltIn(ProgramId.PressLadders)
        repository.sessions.startSession(ladders)
        assertNull(repository.sessions.active.value?.day)
        repository.sessions.discardSession()
        repository.programs.follow(ladders)
        repository.sessions.startSession(ProgramKey.BuiltIn(ProgramId.EasyStrength))
        assertNull(repository.sessions.active.value?.day)
    }

    @Test
    fun `a program made is saved and followed like a built-in`() = runTest {
        val uid = repository.programs.saveProgram(null, pressAndSwings.copy(name = "  Press and swings "))
        val made = repository.programs.customPrograms.value.single()
        assertEquals(ProgramKey.Custom(uid), made.key)
        assertEquals("Press and swings", made.name)
        assertEquals(6, made.totalDays)
        repository.programs.follow(made.key)
        assertEquals(made, repository.programs.following.value)
        assertEquals(BlockKind.Ladder, repository.programs.programDay.value?.blocks?.single()?.kind)
        finishDayOf(made.key)
        assertEquals(2, repository.programs.programDay.value?.number)
        assertEquals(BuiltInExercise.TwoHandSwing, repository.programs.programDay.value?.blocks?.single()?.exercise)
    }

    @Test
    fun `changing a program followed keeps the days done`() = runTest {
        val uid = repository.programs.saveProgram(null, pressAndSwings)
        repository.programs.follow(ProgramKey.Custom(uid))
        finishDayOf(ProgramKey.Custom(uid))
        repository.programs.saveProgram(uid, pressAndSwings.copy(name = "Renamed", weeks = 1))
        assertEquals("Renamed", repository.programs.following.value?.name)
        assertEquals(2, repository.programs.programDay.value?.number)
        assertEquals(1, repository.programs.customPrograms.value.size)
    }

    @Test
    fun `deleting the program followed stops following it`() = runTest {
        val uid = repository.programs.saveProgram(null, pressAndSwings)
        val other = repository.programs.saveProgram(null, pressAndSwings.copy(name = "Other"))
        repository.programs.follow(ProgramKey.Custom(uid))
        repository.programs.deleteProgram(other)
        assertEquals(ProgramKey.Custom(uid), repository.programs.following.value?.key)
        repository.programs.deleteProgram(uid)
        assertNull(repository.programs.following.value)
        assertNull(repository.programs.programDay.value)
        assertTrue(repository.programs.customPrograms.value.isEmpty())
    }

    @Test
    fun `a stored program of only unknown lifts is left out`() = runTest {
        val days = """[[{"exercise":"SeesawPress","kind":"Sets","rounds":3,"reps":5}]]"""
        programDao.rows.value = listOf(CustomProgramEntity("abc", "Future", 3, 4, days, 0))
        followingDao.row.value = FollowingEntity(programId = "custom:abc", startedAtEpochMillis = 0)
        assertTrue(repository.programs.customPrograms.value.isEmpty())
        assertNull(repository.programs.following.value)
    }

    @Test
    fun `a stored block of an unknown lift drops only that block`() = runTest {
        val days = """[[{"exercise":"SeesawPress","kind":"Sets","rounds":3,"reps":5}],""" +
            """[{"exercise":"Press","kind":"Sets","rounds":3,"reps":5,"extra":true}]]"""
        programDao.rows.value = listOf(CustomProgramEntity("abc", "Mixed", 3, 4, days, 0))
        val made = repository.programs.customPrograms.value.single()
        assertEquals(listOf(listOf(Block(BuiltInExercise.Press, rounds = 3, reps = 5))), made.days)
    }

    @Test
    fun `a session plan changed is kept with the session`() = runTest {
        repository.programs.follow(ProgramKey.BuiltIn(ProgramId.GobletSquatRow))
        repository.sessions.startSession(ProgramKey.BuiltIn(ProgramId.GobletSquatRow))
        val plan = repository.programs.programDay.value!!.today.change(0) { it.copy(kg = 28.0) }
        repository.sets.changeSessionPlan { plan }
        assertEquals(plan, repository.sessions.active.value?.plan)
        assertEquals(
            28.0,
            sessionDao.sessions.value.single().plan?.let {
                decodePlan(it, emptyList())
            }?.blocks?.first()?.block?.kg,
        )
    }

    @Test
    fun `a plan change after a stale read still builds on the change before it`() = runTest {
        // Room can hand back the session's row as it was before a write landed, as a set logged re-reads the sets.
        val stale = MutableSharedFlow<List<SessionEntity>>(extraBufferCapacity = 1)
        val lagging = object : SessionDao by sessionDao {
            override fun observeSessions() = merge(sessionDao.observeSessions(), stale)
        }
        val repository = LiftRepository(
            lagging,
            bellDao,
            followingDao,
            programDao,
            exerciseDao,
            bandDao,
            FakeLiftImportDao(sessionDao, bellDao, followingDao, programDao, exerciseDao, bandDao),
            scope,
            clock = { now },
        )
        val key = ProgramKey.BuiltIn(ProgramId.GobletSquatRow)
        repository.programs.follow(key)
        repository.sessions.startSession(key)
        val before = sessionDao.sessions.value
        val rounds = repository.programs.programDay.value!!.today.blocks.first().block.rounds
        repository.sets.changeSessionPlan { repository.today(it).change(0) { block -> block.copy(kg = 28.0) } }
        repository.sets.logSet(BuiltInExercise.GobletSquat, 28.0, 8, null)
        stale.tryEmit(before)
        repository.sets.changeSessionPlan {
            repository.today(it).change(0) { block -> block.copy(rounds = block.rounds + 1) }
        }
        val stored = sessionDao.sessions.value.single().plan?.let { decodePlan(it, emptyList()) }
        assertEquals(28.0, stored?.blocks?.first()?.block?.kg)
        assertEquals(rounds + 1, stored?.blocks?.first()?.block?.rounds)
    }

    /** Today's plan in [session], the way the session's screen builds a change on it. */
    private fun LiftRepository.today(session: LiftSession) = programs.programDay.value!!.copy(plan = session.plan).today

    @Test
    fun `changes to a built-in go in a copy followed from the day after`() = runTest {
        val key = ProgramKey.BuiltIn(ProgramId.GobletSquatRow)
        repository.programs.follow(key)
        finishDayOf(key)
        val day = repository.programs.programDay.value!!.copy(number = 1)
        val changes = planChanges(day.copy(plan = day.today.change(0) { it.copy(kg = 28.0) }))!!
        val copy = repository.programs.saveChanges(changes, SaveScope.ThisDay, "Goblet squat and row (yours)")
        assertTrue(copy is ProgramKey.Custom)
        assertEquals(copy, repository.programs.following.value?.key)
        assertEquals(2, repository.programs.programDay.value?.number)
        assertEquals(28.0, repository.programs.following.value?.days?.single()?.first()?.kg)
        assertEquals("Goblet squat and row (yours)", repository.programs.following.value?.name)
    }

    @Test
    fun `changes to a program made are kept in it and its place stays`() = runTest {
        val uid = repository.programs.saveProgram(null, pressAndSwings)
        val key = ProgramKey.Custom(uid)
        repository.programs.follow(key)
        finishDayOf(key)
        val program = repository.programs.customPrograms.value.single()
        val day = program.dayAfter(0)!!
        val changes = planChanges(day.copy(plan = day.today.change(0) { it.copy(rounds = 3) }))!!
        assertEquals(key, repository.programs.saveChanges(changes, SaveScope.ThisDay, "unused"))
        assertEquals(1, repository.programs.customPrograms.value.size)
        assertEquals(3, repository.programs.customPrograms.value.single().days.first().single().rounds)
        assertEquals(2, repository.programs.programDay.value?.number)
    }

    @Test
    fun `changes on the last day of a built-in are kept in a copy not followed`() = runTest {
        val key = ProgramKey.BuiltIn(ProgramId.GobletSquatRow)
        repository.programs.follow(key)
        val last = program(ProgramId.GobletSquatRow).let { it.dayAfter(it.totalDays - 1)!! }
        val changes = planChanges(last.copy(plan = last.today.change(1) { it.copy(reps = 12) }))!!
        assertFalse(changes.goesOn)
        repository.programs.saveChanges(changes, SaveScope.ThisDay, "Mine")
        assertEquals(key, repository.programs.following.value?.key)
        assertEquals("Mine", repository.programs.customPrograms.value.single().name)
    }

    @Test
    fun `a copy of the built-in followed is followed from the day it was at`() = runTest {
        val key = ProgramKey.BuiltIn(ProgramId.GobletSquatRow)
        repository.programs.follow(key)
        finishDayOf(key)
        val draft = ProgramDraft.of(program(ProgramId.GobletSquatRow)).copy(name = "Mine", weeks = 6)
        val uid = repository.programs.saveCopy(key, draft)
        assertEquals(ProgramKey.Custom(uid), repository.programs.following.value?.key)
        assertEquals(1, repository.programs.followed.value?.daysBefore)
        assertEquals(2, repository.programs.programDay.value?.number)
        assertEquals(6, repository.programs.following.value?.weeks)
    }

    @Test
    fun `a copy of a built-in not followed leaves the program followed`() = runTest {
        val ladders = ProgramKey.BuiltIn(ProgramId.PressLadders)
        repository.programs.follow(ladders)
        val draft = ProgramDraft.of(program(ProgramId.GobletSquatRow)).copy(name = "Mine")
        repository.programs.saveCopy(ProgramKey.BuiltIn(ProgramId.GobletSquatRow), draft)
        assertEquals(ladders, repository.programs.following.value?.key)
        assertEquals("Mine", repository.programs.customPrograms.value.single().name)
    }

    @Test
    fun `a deleted session restored returns to history`() = runTest {
        repository.sessions.startSession()
        repository.sets.logSet(BuiltInExercise.TwoHandSwing, 24.0, 10, Side.Left)
        now += 600_000
        repository.sessions.finishSession("Swings")
        val uid = repository.sessions.sessions.value.single().uid

        repository.sessions.deleteSession(uid)
        assertTrue(repository.sessions.sessions.value.isEmpty())

        now += 1_000
        repository.sessions.restoreSession(uid)
        assertEquals(uid, repository.sessions.sessions.value.single().uid)
    }

    @Test
    fun `a session and a band are stored under ids from the generator`() {
        val repository = FakeLiftStorage().repository(ids = SequentialIds())

        repository.sessions.startSession(null)
        val band = repository.gear.addBand("Red", BandColour.Red)

        assertEquals("id-1", repository.sessions.active.value?.uid)
        assertEquals("id-2", band)
        assertEquals("Red", repository.gear.bands.value.single { it.id == "id-2" }.name)
    }
}
