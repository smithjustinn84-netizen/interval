package dev.justinsmith.bells.data

import dev.justinsmith.core.sync.AppId
import dev.justinsmith.core.sync.SiblingFeed
import dev.justinsmith.core.sync.SiblingSync
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** Bells' side of sharing with Interval: it publishes whenever its sessions change, a delete included. */
fun BellsRepository.siblingSync(
    feed: SiblingFeed,
    scope: CoroutineScope,
    io: CoroutineDispatcher = Dispatchers.IO,
): SiblingSync = SiblingSync(
    self = AppId.Bells,
    feed = feed,
    scope = scope,
    io = io,
    ready = ready,
    changes = sessions,
    sharedSessions = ::sharedSessions,
    importSnapshot = ::importSibling,
)
