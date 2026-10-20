package dev.justinsmith.core.sync

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shares app [self]'s workouts with its sibling on the same device and takes in the sibling's. Once [ready],
 * it publishes the whole set ([sharedSessions], tombstones included) and again whenever [changes] emits;
 * it reads the sibling's when told to ([pull]), on each return to the front, as a phone shows one app at
 * a time. [importSnapshot] stores what it read: the app's repository, the one thing that touches storage.
 */
class SiblingSync(
    private val self: AppId,
    private val feed: SiblingFeed,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    ready: Flow<Boolean>,
    changes: Flow<*>,
    private val sharedSessions: suspend () -> List<SessionRecord>,
    private val importSnapshot: (FeedSnapshot) -> Unit,
) {
    private val log = Logger.withTag("SiblingSync")

    init {
        scope.launch {
            ready.filter { it }.first()
            publish()
            changes.drop(1).collect { publish() }
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
            importSnapshot(snapshot)
        }
    }

    // A failed publish is logged, never thrown: the next change publishes everything again.
    @Suppress("detekt.TooGenericExceptionCaught")
    private suspend fun publish() {
        try {
            val snapshot = FeedSnapshot(source = self, sessions = sharedSessions())
            withContext(io) { feed.publish(FeedCodec.encode(snapshot)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Couldn't publish" }
        }
    }
}
