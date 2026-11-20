package dev.justinsmith.interval.fakes

import dev.justinsmith.interval.platform.LiveHeartRate
import dev.justinsmith.interval.platform.LiveHeartRateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controllable [LiveHeartRate] fake for unit, UI, and acceptance tests. Tracks who holds the stream ([users];
 * [started] while anyone does) and lets tests push any [LiveHeartRateState].
 */
class FakeLiveHeartRate(initial: LiveHeartRateState = LiveHeartRateState.Unavailable) : LiveHeartRate {
    private val _reading = MutableStateFlow(initial)
    override val reading: StateFlow<LiveHeartRateState> = _reading.asStateFlow()

    private val holding = mutableSetOf<Any>()

    /** Who holds the stream now. */
    val users: Set<Any> get() = holding

    val started: Boolean get() = holding.isNotEmpty()

    override fun start(user: Any) {
        holding += user
        if (_reading.value == LiveHeartRateState.Idle) {
            _reading.value = LiveHeartRateState.Connecting
        }
    }

    override fun stop(user: Any) {
        holding -= user
        if (started) return
        if (_reading.value is LiveHeartRateState.Reading || _reading.value == LiveHeartRateState.Connecting) {
            _reading.value = LiveHeartRateState.Idle
        }
    }

    fun emit(state: LiveHeartRateState) {
        _reading.value = state
    }

    fun emitBpm(bpm: Int, contact: Boolean = true) {
        _reading.value = LiveHeartRateState.Reading(bpm, contact)
    }
}
