package dev.justinsmith.interval.storage

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

/**
 * A repository's writes, queued in call order and run one at a time in [scope], so a save can't overtake an
 * earlier one (a clear landing before the insert it followed). A failed write is logged, never thrown:
 * losing one save must not crash a workout.
 */
class SerialWriter(scope: CoroutineScope, private val log: Logger) {
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch { for (write in writes) runLogged(write) }
    }

    /** Queues [write] behind every write before it. */
    fun persist(write: suspend () -> Unit) {
        writes.trySend(write)
    }

    @Suppress("detekt.TooGenericExceptionCaught")
    private suspend fun runLogged(write: suspend () -> Unit) {
        try {
            write()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Couldn't save" }
        }
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
