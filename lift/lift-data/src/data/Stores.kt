package dev.justinsmith.lift.data

import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.BandColour
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.ExerciseDraft
import dev.justinsmith.lift.domain.Followed
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.PerceivedEffort
import dev.justinsmith.lift.domain.PlanChanges
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramDay
import dev.justinsmith.lift.domain.ProgramDraft
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.SaveScope
import dev.justinsmith.lift.domain.SavedTimer
import dev.justinsmith.lift.domain.SessionPlan
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.Side
import kotlinx.coroutines.flow.StateFlow

// The roles LiftRepository plays, one per kind of thing it keeps, so a screen takes only what it reads and
// writes: the gear can't discard a session, and a test can hand a ViewModel a store of its own. The repository is
// each of them; its docs say how each behaves. What only the app does (import, close) stays on it alone, and what
// the interval side reads is LiftActivity.

/** The sessions: those finished, the one in progress, and starting, finishing or dropping one. */
interface SessionStore {
    /** Finished sessions, newest first. */
    val sessions: StateFlow<List<LiftSession>>

    /** The session in progress, if one is. */
    val active: StateFlow<LiftSession?>

    fun startSession(program: ProgramKey? = null)

    fun finishSession(title: String)

    fun reflectOnSession(uid: String, effort: PerceivedEffort?, note: String)

    fun discardSession()

    fun deleteSession(uid: String)

    fun restoreSession(uid: String)
}

/** What goes on in the session in progress: its sets, its plan, the timer running, and how long a rest lasts. */
interface SetStore {
    val restSeconds: StateFlow<Int>

    fun logSet(
        exercise: Exercise,
        bellKg: Double,
        reps: Int,
        side: Side?,
        double: Boolean = false,
        seconds: Int? = null,
        band: Band? = null,
    )

    fun updateSet(set: SetLog)

    fun removeSet(id: Long)

    fun restoreSet(set: SetLog)

    fun changeSessionPlan(change: (LiftSession) -> SessionPlan?)

    fun saveTimer(timer: SavedTimer?)

    fun setRestSeconds(seconds: Int)
}

/** The programs the user made, the one followed, and its day to do next. */
interface ProgramStore {
    val customPrograms: StateFlow<List<Program>>
    val following: StateFlow<Program?>
    val followed: StateFlow<Followed?>
    val programDay: StateFlow<ProgramDay?>

    fun follow(program: ProgramKey, daysDone: Int = 0)

    /** Returns the program's uid. */
    fun saveProgram(uid: String?, draft: ProgramDraft): String

    /** Returns the program the changes were kept in. */
    fun saveChanges(changes: PlanChanges, scope: SaveScope, copyName: String): ProgramKey

    /** Returns the copy's uid. */
    fun saveCopy(of: ProgramKey.BuiltIn, draft: ProgramDraft): String

    fun deleteProgram(uid: String)

    fun stopFollowing()
}

/** The exercises the user added, archived ones too. */
interface ExerciseStore {
    val customExercises: StateFlow<List<CustomExercise>>

    /** Returns the exercise's uid. */
    fun saveExercise(uid: String?, draft: ExerciseDraft): String

    fun setArchived(uid: String, archived: Boolean)
}

/** The bells the user owns, their bar and their bands. */
interface GearStore {
    val bells: StateFlow<List<Double>>
    val bar: StateFlow<Double?>
    val bands: StateFlow<List<Band>>

    fun setOwned(kg: Double, owned: Boolean)

    fun setBar(kg: Double?)

    fun saveBand(band: Band)

    fun saveBands(bands: List<Band>)

    /** Returns the new band's id. */
    fun addBand(name: String, colour: BandColour): String

    fun removeBand(band: Band)
}
