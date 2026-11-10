package dev.justinsmith.lift.fakes

import dev.justinsmith.lift.data.LiftReadiness
import dev.justinsmith.lift.domain.ReadinessReason
import kotlinx.coroutines.flow.MutableStateFlow

/** A [LiftReadiness] a test sets: an ordinary day until it says otherwise, counting each easier day [used]. */
class FakeLiftReadiness : LiftReadiness {
    override val taxedBy = MutableStateFlow<ReadinessReason?>(null)
    override val easierAhead = MutableStateFlow(false)
    var used = 0
        private set

    override fun useEasier() {
        used++
    }
}
