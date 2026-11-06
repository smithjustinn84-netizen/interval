package dev.justinsmith.interval.data

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.storage.SerialWriter
import dev.justinsmith.interval.storage.orEmptyOnError
import dev.justinsmith.interval.timer.Flow
import dev.justinsmith.interval.timer.builtInFlows
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The mobility flows, the shipped ones and the user's own, which follow the database: the [FlowStore]. Written
 * through [writer], the queue every interval repository shares. A flow is named against what is stored, inside the
 * write, as a preset is, so two saved in a row never take one name.
 */
class FlowRepository(
    private val flowDao: FlowDao,
    private val writer: SerialWriter,
    scope: CoroutineScope,
) : FlowStore {
    private val log = Logger.withTag("FlowRepository")

    private val _ready = MutableStateFlow(false)

    /** True once the user's flows have been read. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _flows = MutableStateFlow(builtInFlows)

    /** Every flow: the shipped ones, then the user's in the order they were saved. */
    override val flows: StateFlow<List<Flow>> = _flows.asStateFlow()

    override val customFlows: StateFlow<List<Flow>> =
        flows.map { all -> all.filter { it.custom } }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch {
            flowDao.observeAll()
                .map { rows -> rows.mapNotNull { it.toDomain() } }
                .orEmptyOnError("flows", log)
                .collect { custom ->
                    _flows.value = builtInFlows + custom
                    _ready.value = true
                }
        }
    }

    /** Saves [flow] as a new one of the user's own, under a name no other flow has. */
    override fun saveFlow(flow: Flow) {
        writer.persist { flowDao.insert(flow.copy(id = null).named().toEntity()) }
    }

    /** Rewrites one of the user's own flows, found by its id. */
    override fun updateFlow(flow: Flow) {
        if (flow.id == null) return
        writer.persist { flowDao.update(flow.named().toEntity()) }
    }

    /** Removes one of the user's own flows; a shipped one has no id and is left alone. */
    override fun deleteFlow(flow: Flow) {
        val id = flow.id ?: return
        writer.persist { flowDao.delete(id) }
    }

    /** Puts a flow [deleteFlow] removed back under its own id: Undo. */
    override fun restoreFlow(flow: Flow) {
        if (flow.id == null) return
        writer.persist { flowDao.insert(flow.named().toEntity()) }
    }

    private suspend fun Flow.named(): Flow {
        val stored = flowDao.all().mapNotNull { it.toDomain() }
        val taken = (builtInFlows + stored).filterNot { id != null && it.id == id }.map { it.name }
        return copy(name = uniqueFlowName(name, taken))
    }
}

/** [name] as a flow can be stored beside [taken]: itself, else with " 2", " 3"… within [Flow.NAME_MAX_LENGTH]. */
internal fun uniqueFlowName(name: String, taken: Collection<String>): String {
    val wanted = name.trim().take(Flow.NAME_MAX_LENGTH)
    if (wanted !in taken) return wanted
    return generateSequence(2) { it + 1 }
        .map { number -> "${wanted.take(Flow.NAME_MAX_LENGTH - " $number".length).trimEnd()} $number" }
        .first { it !in taken }
}
