package dev.justinsmith.interval.data

import co.touchlab.kermit.Logger
import dev.justinsmith.core.sync.AppId
import dev.justinsmith.core.sync.FeedCodec
import dev.justinsmith.core.sync.FeedSnapshot
import dev.justinsmith.core.sync.SiblingFeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shares this app's workouts with its sibling app on the same device, and takes in the sibling's. Each app
 * publishes its whole set, tombstones included, whenever its history changes; it reads the sibling's when
 * it comes to the front ([pull]), as a phone shows one app at a time. The sibling's workouts are stored by
 * [repository], which is the only thing that touches storage.
 */
class SiblingSync(
    private val repository: IntervalRepository,
    private val feed: SiblingFeed,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val log = Logger.withTag("SiblingSync")

    init {
        scope.launch {
            repository.ready.filter { it }.first()
            publish()
            // Every later change to the history, a delete included, publishes again.
            repository.history.drop(1).collect { publish() }
        }
    }

    /** Reads the sibling's workouts and stores them, in step with what it shares now. */
    fun pull() {
        scope.launch {
            val text = withContext(io) { feed.readSibling() } ?: return@launch
            val snapshot = FeedCodec.decode(text)
            if (snapshot == null) {
                log.w { "Couldn't read the sibling's feed" }
                return@launch
            }
            repository.importSibling(snapshot)
        }
    }

    // A failed publish is logged, never thrown: the next change publishes everything again.
    @Suppress("detekt.TooGenericExceptionCaught")
    private suspend fun publish() {
        try {
            val snapshot = FeedSnapshot(source = AppId.Interval, sessions = repository.sharedSessions())
            withContext(io) { feed.publish(FeedCodec.encode(snapshot)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Couldn't publish" }
        }
    }
}
