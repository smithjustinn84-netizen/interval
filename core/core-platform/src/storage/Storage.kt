package dev.justinsmith.interval.storage

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

/**
 * A scope for a repository's reads and writes, on [dispatcher], for as long as the container that owns it. A
 * failure that gets past the repository's own handling is logged here rather than handed to the platform,
 * which would end the app: a storage problem must not crash a workout.
 */
fun storageScope(dispatcher: CoroutineDispatcher, name: String): CoroutineScope {
    val log = Logger.withTag(name)
    return CoroutineScope(SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, e -> log.e(e) { "Failed" } })
}

/** A write that failed, for the app to tell the user: [cause] is what the storage threw. */
class StorageError(val cause: Exception)

/**
 * A repository's writes, queued in call order and run one at a time in [scope], so a save can't overtake an
 * earlier one (a clear landing before the insert it followed). A failed write is logged and published on
 * [errors], never thrown: losing one save must not crash a workout, but the user should know.
 */
class SerialWriter(scope: CoroutineScope, private val log: Logger) {
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val consumer = scope.launch { for (write in writes) runLogged(write) }

    // A few kept for a collector still busy with the last, the oldest dropped past that: one shown is enough.
    private val failures = MutableSharedFlow<StorageError>(
        extraBufferCapacity = ERRORS_KEPT,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Each [persist] that failed, as it fails. An [await]'s failure is its caller's, thrown there, not here. */
    val errors: SharedFlow<StorageError> = failures.asSharedFlow()

    /** Queues [write] behind every write before it. After [close], it is dropped. */
    fun persist(write: suspend () -> Unit) {
        if (writes.trySend(write).isClosed) log.w { "A save came after the writer closed and was dropped" }
    }

    /**
     * Queues [write] as [persist] does, then waits for it and hands back what it returns: for a write the caller
     * must see land, or fail. Its failure is thrown here, not logged. After [close] it throws at once.
     */
    @Suppress("detekt.TooGenericExceptionCaught")
    suspend fun <T> await(write: suspend () -> T): T {
        val done = CompletableDeferred<T>()
        val queued = writes.trySend {
            try {
                done.complete(write())
            } catch (e: CancellationException) {
                done.cancel(e)
                throw e
            } catch (e: Exception) {
                done.completeExceptionally(e)
            }
        }
        check(!queued.isClosed) { "The writer is closed" }
        return done.await()
    }

    /**
     * Takes no more writes and returns once every write queued before has run, so the storage under them can
     * close without losing the last ones. Returns at once if [scope] has already been cancelled.
     */
    suspend fun close() {
        writes.close()
        consumer.join()
    }

    @Suppress("detekt.TooGenericExceptionCaught")
    private suspend fun runLogged(write: suspend () -> Unit) {
        try {
            write()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Couldn't save" }
            failures.tryEmit(StorageError(e))
        }
    }

    private companion object {
        const val ERRORS_KEPT = 8
    }
}

/**
 * A read that shows [fallback] at once when it fails, so the app still starts, then is tried again a few
 * times: `catch` alone ends the flow, and one bad read would stop it following the database for good.
 */
fun <T> Flow<T>.orOnError(fallback: T, what: String, log: Logger): Flow<T> =
    retryWhen { e, attempt ->
        log.e(e) { "Couldn't read $what" }
        if (attempt == 0L) emit(fallback)
        val again = e !is CancellationException && attempt < READ_RETRIES
        if (again) delay(READ_RETRY_DELAY_MILLIS * (attempt + 1))
        again
    }.catch { emit(fallback) }

/** [orOnError] for a list, empty while it can't be read. */
fun <T> Flow<List<T>>.orEmptyOnError(what: String, log: Logger): Flow<List<T>> = orOnError(emptyList(), what, log)

internal const val READ_RETRIES = 3L
internal const val READ_RETRY_DELAY_MILLIS = 500L
