package dev.justinsmith.interval.mobility

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.FlowStore
import dev.justinsmith.interval.data.SettingsStore
import dev.justinsmith.interval.timer.Flow
import dev.justinsmith.interval.timer.FlowUse
import dev.justinsmith.interval.timer.builtInFlows
import dev.justinsmith.interval.timer.formatClock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** A flow as the page lists it: with its [total] as a clock, the user's get-ready countdown in it. */
data class FlowItem(val flow: Flow, val total: String)

/** The flows by when each is for, in the order they are listed, and the get-ready they start after. */
data class MobilityUiState(
    val before: List<FlowItem> = emptyList(),
    val after: List<FlowItem> = emptyList(),
    val prepSeconds: Int = 0,
) {
    /** The flow [key] names, as listed; null for one that isn't. */
    fun item(key: String?): FlowItem? = (before + after).firstOrNull { it.flow.key == key }
}

/**
 * The Mobility page: the flows the app ships with and the user's own, each in its group, and the get-ready
 * countdown, which every workout starts after and a flow's total counts.
 */
class MobilityViewModel(settingsStore: SettingsStore, flowStore: FlowStore) : ViewModel() {
    val state: StateFlow<MobilityUiState> =
        combine(settingsStore.config, flowStore.flows) { config, flows -> mobilityState(config.prepSeconds, flows) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                mobilityState(settingsStore.config.value.prepSeconds, flowStore.flows.value),
            )
}

internal fun mobilityState(prepSeconds: Int, flows: List<Flow> = builtInFlows): MobilityUiState {
    val items = flows.map { FlowItem(it, formatClock(it.config(prepSeconds).totalSeconds)) }
    return MobilityUiState(
        before = items.filter { it.flow.use == FlowUse.Before },
        after = items.filter { it.flow.use == FlowUse.After },
        prepSeconds = prepSeconds,
    )
}
