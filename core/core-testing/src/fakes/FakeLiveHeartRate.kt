package dev.justinsmith.interval.fakes

import dev.justinsmith.interval.platform.LiveHeartRate
import dev.justinsmith.interval.platform.LiveHeartRateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controllable [LiveHeartRate] fake for unit, UI, and acceptance tests.
 * Tracks [started] and how many times [start]/[stop] were called, and lets tests push any [LiveHeartRateState].
 */
class FakeLiveHeartRate(initial: LiveHeartRateState = LiveHeartRateState.Unavailable) : LiveHeartRate {
    private val _reading = MutableStateFlow(initial)
    override val reading: StateFlow<LiveHeartRateState> = _reading.asStateFlow()

    var started: Boolean = false
        private set
    var startCount: Int = 0
        private set
    var stopCount: Int = 0
        private set

    override fun start() {
        started = true
        startCount++
        if (_reading.value == LiveHeartRateState.Idle) {
            _reading.value = LiveHeartRateState.Connecting
        }
    }

    override fun stop() {
        started = false
        stopCount++
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
