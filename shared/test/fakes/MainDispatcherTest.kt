@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.fakes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * A test of something that runs on `Dispatchers.Main`, as every ViewModel's scope does: [dispatcher]
 * stands in for it during each test. Unconfined by default, so state has settled by the time a call returns.
 */
abstract class MainDispatcherTest(protected val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) {
    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()
}
