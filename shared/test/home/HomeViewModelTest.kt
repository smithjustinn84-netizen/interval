package dev.justinsmith.interval.home

import app.cash.turbine.test
import dev.justinsmith.interval.Greeting
import dev.justinsmith.interval.Platform
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeViewModelTest {
    private val fakePlatform = object : Platform {
        override val name = "Test"
    }

    @Test
    fun `initial state carries the platform greeting with details hidden`() {
        val state = HomeViewModel(Greeting(fakePlatform)).state.value
        assertEquals("Hello, Test!", state.greeting)
        assertFalse(state.showDetails)
    }

    @Test
    fun `toggling details flips visibility each time`() = runTest {
        val viewModel = HomeViewModel(Greeting(fakePlatform))
        viewModel.state.test {
            assertFalse(awaitItem().showDetails)
            viewModel.toggleDetails()
            assertTrue(awaitItem().showDetails)
            viewModel.toggleDetails()
            assertFalse(awaitItem().showDetails)
        }
    }
}
