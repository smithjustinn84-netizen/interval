package dev.justinsmith.bells.data

import dev.justinsmith.core.sync.AppId
import dev.justinsmith.core.sync.SiblingFeed
import dev.justinsmith.core.sync.SiblingSync
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.combine

/**
 * Bells' side of sharing with Interval: it publishes whenever its sessions change, a delete included, or the
 * shared settings do (passed on as they were last changed, in either app).
 */
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
    changes = combine(sessions, sharedSettings) { sessions, settings -> sessions to settings },
    sharedSessions = ::sharedSessions,
    importSnapshot = ::importSibling,
    sharedSettings = { sharedSettings.value },
)
