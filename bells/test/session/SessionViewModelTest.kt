@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.bells.session

import androidx.lifecycle.viewModelScope
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.data.ExerciseFilterStore
import dev.justinsmith.bells.data.SessionEntity
import dev.justinsmith.bells.data.SetEntity
import dev.justinsmith.bells.domain.BAR_KG_STEP
import dev.justinsmith.bells.domain.BuiltInExercise
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.ProgramId
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.bells.fakes.FakeIntervalActivity
import dev.justinsmith.bells.fakes.FixedTimestampFormatter
import dev.justinsmith.bells.fakes.RecordingBeeper
import dev.justinsmith.interval.platform.WallClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The sheet's draft, which the Next set card composes: what a lift picked starts from, and how it steps. */
class SessionViewModelTest {
    private val storage = FakeBellsStorage()
    private var now = 100_000L
    private val repository = with(storage) {
        BellsRepository(
            sessions,
            bells,
            following,
            programs,
            exercises,
            bands,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        ) { now }
    }

    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun viewModel() = SessionViewModel(
        repository,
        WallClock { now },
        RecordingBeeper(),
        ExerciseFilterStore(),
        FakeIntervalActivity(),
        FixedTimestampFormatter,
    )

    private inline fun <T> SessionViewModel.use(block: SessionViewModel.() -> T): T = try {
        block()
    } finally {
        viewModelScope.cancel()
    }

    /** A set of [exercise] in a session finished before now, [kg] × [reps] on [side], with [band] if any. */
    private fun earlier(
        id: Long,
        exercise: Exercise,
        kg: Double,
        reps: Int = 8,
        side: Side? = null,
        band: String? = null,
    ) {
        storage.sessions.sessions.update { all ->
            if (all.any { it.uid == EARLIER }) all else all + SessionEntity(EARLIER, 0, 60_000, "Earlier", 60_000)
        }
        storage.sessions.sets.update {
            it + SetEntity(
                id,
                EARLIER,
                exercise.key,
                kg,
                reps,
                side?.name,
                loggedAtEpochMillis = id,
                equipment = exercise.equipment.name,
                band = band,
            )
        }
    }

    @Test
    fun `picking a lift suggests the bell it was last done with and shows that set as last time`() {
        earlier(1, BuiltInExercise.Press, 12.0, side = Side.Left)
        viewModel().use {
            updateDraft { it.copy(exercise = BuiltInExercise.Press) }
            assertEquals(12.0, state.value.draft.bellKg)
            assertEquals(1L, state.value.draftLastTime?.id)
        }
    }

    @Test
    fun `picking a lift never done keeps the bell in hand`() {
        viewModel().use {
            updateDraft { it.copy(bellKg = 24.0) }
            updateDraft { it.copy(exercise = BuiltInExercise.Press) }
            assertEquals(24.0, state.value.draft.bellKg)
            assertNull(state.value.draftLastTime)
        }
    }

    @Test
    fun `picking band work suggests its last band`() {
        val heavy = repository.bands.value.last()
        earlier(1, BuiltInExercise.BandPullApart, 0.0, band = heavy.id)
        viewModel().use {
            updateDraft { it.copy(exercise = BuiltInExercise.BandPullApart) }
            assertEquals(heavy.id, state.value.draft.band.id)
        }
    }

    @Test
    fun `picking a bodyweight lift suggests the weight last added`() {
        earlier(1, BuiltInExercise.PushUp, 8.0)
        viewModel().use {
            updateDraft { it.copy(exercise = BuiltInExercise.PushUp) }
            assertEquals(8.0, state.value.draft.addedKg)
        }
    }

    @Test
    fun `the load steps among the owned bells and stops at the ends`() {
        repository.setOwned(16.0, owned = true)
        repository.setOwned(24.0, owned = true)
        viewModel().use {
            assertEquals(16.0, state.value.draft.bellKg)
            stepDraftLoad(1)
            assertEquals(24.0, state.value.draft.bellKg)
            stepDraftLoad(1)
            assertEquals(24.0, state.value.draft.bellKg)
            stepDraftLoad(-1)
            stepDraftLoad(-1)
            assertEquals(16.0, state.value.draft.bellKg)
        }
    }

    @Test
    fun `the bar steps by its plates and never below the bar`() {
        repository.setBar(20.0)
        viewModel().use {
            updateDraft { it.copy(exercise = BuiltInExercise.BarbellDeadlift) }
            assertEquals(20.0, state.value.draft.barKg)
            stepDraftLoad(-1)
            assertEquals(20.0, state.value.draft.barKg)
            stepDraftLoad(1)
            assertEquals(20.0 + BAR_KG_STEP, state.value.draft.barKg)
        }
    }

    @Test
    fun `a band steps among the bands offered and the mat has no load to step`() {
        val offered = repository.bands.value
        viewModel().use {
            updateDraft { it.copy(exercise = BuiltInExercise.BandPullApart) }
            val at = offered.indexOfFirst { it.id == state.value.draft.band.id }
            stepDraftLoad(1)
            assertEquals(offered[at + 1].id, state.value.draft.band.id)

            updateDraft { it.copy(exercise = BuiltInExercise.Hundred) }
            val before = state.value.draft
            stepDraftLoad(1)
            assertEquals(before, state.value.draft)
        }
    }

    @Test
    fun `logging the card's set keeps the draft and flips the side of a one-sided lift`() {
        viewModel().use {
            updateDraft { it.copy(exercise = BuiltInExercise.Press, side = Side.Left) }
            logSet()
            assertEquals(1, repository.active.value?.sets?.size)
            assertEquals(BuiltInExercise.Press, state.value.draft.exercise)
            assertEquals(Side.Right, state.value.draft.side)
        }
    }

    @Test
    fun `a resumed session starts from its last set on the other side`() {
        viewModel().use {
            updateDraft { it.copy(exercise = BuiltInExercise.Press, bellKg = 24.0, reps = 5, side = Side.Left) }
            logSet()
        }
        viewModel().use {
            val draft = state.value.draft
            assertEquals(BuiltInExercise.Press, draft.exercise)
            assertEquals(24.0, draft.bellKg)
            assertEquals(5, draft.reps)
            assertEquals(Side.Right, draft.side)
        }
    }

    @Test
    fun `a free session is free and a program day is not`() {
        viewModel().use { assertTrue(state.value.free) }
        repository.discardSession()
        val program = ProgramKey.BuiltIn(ProgramId.FirstHundredSwings)
        repository.follow(program)
        repository.startSession(program)
        viewModel().use {
            assertFalse(state.value.free)
            assertNotNull(state.value.upNext)
            assertNull(state.value.draftLastTime)
        }
    }

    @Test
    fun `last time is the lift's last set before this session not one just logged`() {
        earlier(1, BuiltInExercise.Press, 12.0)
        viewModel().use {
            updateDraft { it.copy(exercise = BuiltInExercise.Press) }
            logSet()
            assertEquals(1L, state.value.draftLastTime?.id)
        }
    }

    private companion object {
        const val EARLIER = "earlier"
    }
}
