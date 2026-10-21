package dev.justinsmith.interval.data

import dev.justinsmith.core.sync.AppId
import dev.justinsmith.core.sync.SiblingFeed
import dev.justinsmith.core.sync.SiblingSync
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Interval's side of sharing with Bells: it publishes whenever its history changes, a delete included, or
 * the shared settings do, and stores what Bells shares through this repository.
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
    changes = combine(history, appSettings.map { it.shared }.distinctUntilChanged()) { history, shared ->
        history to
            shared
    },
    sharedSessions = ::sharedSessions,
    importSnapshot = ::importSibling,
    sharedSettings = { appSettings.value.shared },
)
