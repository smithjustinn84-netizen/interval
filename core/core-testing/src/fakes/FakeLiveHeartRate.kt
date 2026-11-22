package dev.justinsmith.interval.fakes

import dev.justinsmith.interval.platform.LiveHeartRate
import dev.justinsmith.interval.platform.LiveHeartRateState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controllable [LiveHeartRate] fake for unit, UI, and acceptance tests. Tracks who holds the stream ([users];
 * [started] while anyone does) and lets tests push any [LiveHeartRateState].
 */
class FakeLiveHeartRate(initial: LiveHeartRateState = LiveHeartRateState.Unavailable) : LiveHeartRate {
    private val _reading = MutableStateFlow(initial)
    override val reading: StateFlow<LiveHeartRateState> = _reading.asStateFlow()

    private val _readings = MutableSharedFlow<Int>(extraBufferCapacity = READINGS_BUFFER)
    override val readings: Flow<Int> = _readings.asSharedFlow()

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

    /** The stream at [state]; a reading with contact is also one read, as a sensor's is. */
    fun emit(state: LiveHeartRateState) {
        _reading.value = state
        if (state is LiveHeartRateState.Reading && state.contact) _readings.tryEmit(state.bpm)
    }

    fun emitBpm(bpm: Int, contact: Boolean = true) = emit(LiveHeartRateState.Reading(bpm, contact))

    private companion object {
        const val READINGS_BUFFER = 64
    }
}
