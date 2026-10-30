@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.storage

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StorageTest {
    private val log = Logger.withTag("StorageTest")

    @Test
    fun `writes run in the order queued and one failing doesn't stop the rest`() = runTest {
        val writer = SerialWriter(backgroundScope, log)
        val done = mutableListOf<Int>()
        val last = CompletableDeferred<Unit>()
        writer.persist { done += 1 }
        writer.persist { error("disk full") }
        writer.persist { done += 2 }
        writer.persist { last.complete(Unit) }
        last.await()
        assertEquals(listOf(1, 2), done)
    }

    @Test
    fun `closing waits for every write already queued and drops any after`() = runTest {
        val writer = SerialWriter(backgroundScope, log)
        val done = mutableListOf<Int>()
        writer.persist {
            delay(100)
            done += 1
        }
        writer.persist { done += 2 }
        writer.close()
        writer.persist { done += 3 }
        runCurrent()
        assertEquals(listOf(1, 2), done)
    }

    @Test
    fun `a failure that reaches the storage scope is logged and the scope runs on`() = runTest {
        val scope = storageScope(StandardTestDispatcher(testScheduler), "test")
        scope.launch { error("disk full") }
        runCurrent()
        assertTrue(scope.isActive)
        val after = CompletableDeferred<Unit>()
        scope.launch { after.complete(Unit) }
        after.await()
        scope.cancel()
    }

    @Test
    fun `a failed read shows the fallback then follows the source again once it reads`() = runTest {
        var attempts = 0
        val source = flow {
            if (attempts++ == 0) error("database locked")
            emit(listOf("swings"))
        }
        assertEquals(listOf(emptyList(), listOf("swings")), source.orEmptyOnError("test", log).take(2).toList())
    }

    @Test
    fun `a read that never succeeds settles on the fallback`() = runTest {
        val source = flow<Int?> { error("gone") }
        assertEquals(listOf(null, null), source.orOnError(null, "test", log).toList())
    }
}
