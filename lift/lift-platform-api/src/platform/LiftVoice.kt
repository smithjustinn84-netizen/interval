package dev.justinsmith.lift.platform

import dev.justinsmith.lift.domain.DensityCall
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.SafetyLine

/** A line Lift says in a session, about [exercise]. Never one mid-rep: the timers say each where a lifter is still. */
sealed interface Coaching {
    val exercise: Exercise

    /**
     * One of the lift's cues, the [index]th, taken round as many as it has: an EMOM minute's at its count-in (the
     * minute's own, so they rotate), a density block's at its count-in, and the set-up (the first, 0) of the lift
     * coming in a rest's last seconds.
     */
    data class Cue(override val exercise: Exercise, val index: Int) : Coaching

    /**
     * One of the faults the lift is prone to, the [index]th, taken round as many as it has: said once early in the
     * rest after a set of it. A lift with none, any but the ballistic and hinge lifts, says nothing.
     */
    data class Fault(override val exercise: Exercise, val index: Int) : Coaching

    /** A density block's cap of [exercise] has this [call] due: five minutes left, one, or time with its [rounds]. */
    data class Clock(override val exercise: Exercise, val call: DensityCall, val rounds: Int) : Coaching

    /**
     * A safety [line] before a set of [exercise], said in place of the set-up cue that moment would say, or as a
     * program day's session opens on it.
     */
    data class Safety(override val exercise: Exercise, val line: SafetyLine) : Coaching
}

/**
 * What Lift says aloud, through the process's speaker, behind the voice switch the interval side's cues share. The
 * timers hand it typed lines, which it puts into words. Implementations swallow failures: a missing line must never
 * stop a session.
 */
fun interface LiftVoice {
    fun say(line: Coaching)
}
