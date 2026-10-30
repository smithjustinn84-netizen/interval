@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.bells.gear

import dev.justinsmith.bells.domain.BandColour
import dev.justinsmith.bells.domain.BandLevel
import dev.justinsmith.bells.domain.PersonalRecord
import dev.justinsmith.bells.domain.offeredBands
import dev.justinsmith.bells.fakes.FakeBellsStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Your equipment: the bells owned, the bar, the bands in their order, and the records across every session. */
class GearViewModelTest {
    private val storage = FakeBellsStorage()
    private val repository = storage.repository()

    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun viewModel() = GearViewModel(repository)

    @Test
    fun `a bell toggled is owned and toggled again let go`() {
        val viewModel = viewModel()

        viewModel.toggle(16.0)
        assertEquals(setOf(16.0), viewModel.state.value.owned)

        viewModel.toggle(16.0)
        assertEquals(emptySet(), viewModel.state.value.owned)
    }

    @Test
    fun `the bar is set and taken away`() {
        val viewModel = viewModel()

        viewModel.setBar(15.0)
        assertEquals(15.0, viewModel.state.value.bar)

        viewModel.setBar(null)
        assertNull(viewModel.state.value.bar)
    }

    @Test
    fun `a band added goes heaviest and is then renamed moved lighter and removed`() {
        val viewModel = viewModel()
        val starters = offeredBands(viewModel.state.value.bands).size

        viewModel.saveBand(null, " Purple ", BandColour.Purple)
        val added = viewModel.state.value.bands.single { it.level == null }
        assertEquals("Purple", added.name)
        assertEquals(BandColour.Purple, added.colour)
        assertEquals(starters, offeredBands(viewModel.state.value.bands).indexOfFirst { it.id == added.id })

        viewModel.saveBand(added, "Violet", BandColour.Black)
        val renamed = viewModel.state.value.bands.single { it.id == added.id }
        assertEquals("Violet" to BandColour.Black, renamed.name to renamed.colour)

        viewModel.move(renamed, -1)
        assertEquals(starters - 1, offeredBands(viewModel.state.value.bands).indexOfFirst { it.id == added.id })

        // One of the user's own with no sets goes altogether.
        viewModel.remove(viewModel.state.value.bands.single { it.id == added.id })
        assertTrue(viewModel.state.value.bands.none { it.id == added.id })
    }

    @Test
    fun `a starting band removed is archived and can be restored`() {
        val viewModel = viewModel()
        val light = viewModel.state.value.bands.single { it.level == BandLevel.ExtraLight }

        viewModel.remove(light)
        assertTrue(viewModel.state.value.bands.single { it.id == light.id }.archived)
        assertFalse(offeredBands(viewModel.state.value.bands).any { it.id == light.id })

        viewModel.restore(viewModel.state.value.bands.single { it.id == light.id })
        assertFalse(viewModel.state.value.bands.single { it.id == light.id }.archived)
    }

    @Test
    fun `records come from every session finished`() {
        storage.finished("a", "Swings", 0, 60_000, 16.0 to 10)
        storage.finished("b", "More swings", 100_000, 160_000, 24.0 to 5)

        val records = viewModel().state.value.records

        assertEquals(listOf(24.0), records.map { (it as PersonalRecord.Weighted).heaviestKg })
        assertEquals(listOf(10), records.map { (it as PersonalRecord.Weighted).mostReps })
        assertEquals(emptySet(), viewModel().state.value.usedBands)
    }
}
