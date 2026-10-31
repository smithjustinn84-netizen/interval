package dev.justinsmith.interval.history

import dev.justinsmith.interval.model.LiftWorkout
import dev.justinsmith.lift.domain.LiftSession

/** The finished sessions among these, newest first as they came, as History and Plan count them. */
internal fun List<LiftSession>.asLiftWorkouts(): List<LiftWorkout> = mapNotNull { session ->
    session.finishedAtEpochMillis?.let { LiftWorkout(session.uid, it, session.elapsedSeconds(it), session.title) }
}
