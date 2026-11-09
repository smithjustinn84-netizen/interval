package dev.justinsmith.interval.mobility

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.FlowStore
import dev.justinsmith.interval.data.PresetStore
import dev.justinsmith.interval.data.SettingsStore
import dev.justinsmith.interval.timer.Clash
import dev.justinsmith.interval.timer.Flow
import dev.justinsmith.interval.timer.FlowDraft
import dev.justinsmith.interval.timer.FlowUse
import dev.justinsmith.interval.timer.Move
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.keyed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A shipped flow to start a copy of, by its [key], under [name] ("Hips and shoulders (yours)"). */
data class FlowCopy(val key: String, val name: String)

/**
 * The editor as it stands: the [draft], whether it [edits] a flow already saved, what it would [clash] with, and
 * its [total] time after the user's get-ready, as a clock; null with no move yet.
 */
data class FlowEditorUiState(
    val draft: FlowDraft = FlowDraft(),
    val edits: Boolean = false,
    val clash: Clash? = null,
    val total: String? = null,
) {
    /** Whether Save would save it: named, with a move, every move named, and like no other flow or preset. */
    val canSave: Boolean get() = draft.toFlow() != null && clash == null
}

private const val KEY_DRAFT = "flow_draft"

/**
 * One flow being built: a new one, a copy of a shipped one ([copy]) or one of the user's own ([id]). The draft is
 * kept in [savedState] as it changes, so the page comes back as it was left. Saving refuses a draft that is not a
 * flow yet, or that has the moves and times of another flow or a preset, which a finished workout could not be told
 * apart from.
 */
class FlowEditorViewModel(
    private val flowStore: FlowStore,
    presetStore: PresetStore,
    settingsStore: SettingsStore,
    private val id: Long?,
    copy: FlowCopy?,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val draft = MutableStateFlow(restored() ?: initial(copy))

    val state: StateFlow<FlowEditorUiState> = combine(
        draft,
        flowStore.flows,
        presetStore.presets,
        settingsStore.config,
    ) { draft, flows, presets, config ->
        FlowEditorUiState(
            draft = draft,
            edits = id != null,
            clash = draft.clash(flows, presets),
            total = draft.toFlow()?.let { formatClock(it.config(config.prepSeconds).totalSeconds) },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FlowEditorUiState(draft.value, edits = id != null))

    fun change(transform: (FlowDraft) -> FlowDraft) {
        val next = transform(draft.value)
        draft.value = next
        savedState[KEY_DRAFT] = json.encodeToString(next.stored())
    }

    /** Saves the flow; false, with nothing written, while [FlowEditorUiState.canSave] is false. */
    fun save(): Boolean {
        val now = state.value
        val flow = now.draft.toFlow()
        if (flow == null || now.clash != null) return false
        if (flow.id == null) flowStore.saveFlow(flow) else flowStore.updateFlow(flow)
        return true
    }

    /** Deletes the flow edited; returns it for Undo ([restore]), null for a new one. */
    fun delete(): Flow? {
        val flow = flowStore.customFlows.value.firstOrNull { it.id == id } ?: return null
        flowStore.deleteFlow(flow)
        return flow
    }

    fun restore(flow: Flow) {
        flowStore.restoreFlow(flow)
    }

    private fun initial(copy: FlowCopy?): FlowDraft {
        val own = id?.let { id -> flowStore.customFlows.value.firstOrNull { it.id == id } }
        val copied = copy?.let { wanted ->
            flowStore.flows.value.keyed(wanted.key)?.let { FlowDraft.copyOf(it, wanted.name) }
        }
        return own?.let(FlowDraft::of) ?: copied ?: FlowDraft()
    }

    private fun restored(): FlowDraft? = savedState.get<String>(KEY_DRAFT)?.let { text ->
        runCatching { json.decodeFromString<StoredDraft>(text).toDraft(id) }.getOrNull()
    }
}

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private class StoredDraft(val name: String, val use: String, val note: String, val moves: List<StoredMove>) {
    fun toDraft(id: Long?) = FlowDraft(
        name,
        FlowUse.entries.firstOrNull { it.name == use } ?: FlowUse.After,
        note,
        moves.map { Move(it.name, it.seconds.coerceIn(Move.SECONDS_RANGE), it.cue, it.sided, it.held, it.exercise) },
        id,
    )
}

@Serializable
private class StoredMove(
    val name: String,
    val seconds: Int,
    val cue: String,
    val sided: Boolean,
    val held: Boolean,
    val exercise: String? = null,
)

private fun FlowDraft.stored() =
    StoredDraft(
        name,
        use.name,
        note,
        moves.map { StoredMove(it.name, it.seconds, it.cue, it.sided, it.held, it.exercise) },
    )
