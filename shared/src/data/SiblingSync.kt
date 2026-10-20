package dev.justinsmith.interval.data

import dev.justinsmith.core.sync.AppId
import dev.justinsmith.core.sync.SiblingFeed
import dev.justinsmith.core.sync.SiblingSync
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Interval's side of sharing with Bells: it publishes whenever its history changes, a delete included,
 * and stores what Bells shares through this repository.
 */
fun IntervalRepository.siblingSync(
    feed: SiblingFeed,
    scope: CoroutineScope,
    io: CoroutineDispatcher = Dispatchers.IO,
): SiblingSync = SiblingSync(
    self = AppId.Interval,
    feed = feed,
    scope = scope,
    io = io,
    ready = ready,
    changes = history,
    sharedSessions = ::sharedSessions,
    importSnapshot = ::importSibling,
)
