package dev.justinsmith.lift.session

import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.ExerciseFilters
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramDay
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.nextPlanned
import dev.justinsmith.lift.domain.offeredBands
import dev.justinsmith.lift.domain.offeredExercises
import dev.justinsmith.lift.domain.ownedEquipment
import dev.justinsmith.lift.domain.planRows
import dev.justinsmith.lift.domain.recentExercises
import dev.justinsmith.lift.domain.recordSetIds
import dev.justinsmith.lift.domain.setsOldestFirst
import dev.justinsmith.lift.domain.standardBells
import dev.justinsmith.lift.library.LibraryList

/** Every set of the sessions before this one, as stored now: what a set has to beat, and goes by. */
internal class Before(
    /** In any order: what a set logged now has to beat to be a record. */
    val all: List<SetLog>,
    /** Oldest first, for the suggestions and the last time each lift was done. */
    val oldestFirst: List<SetLog>,
) {
    companion object {
        fun of(sessions: List<LiftSession>) = Before(sessions.flatMap { it.sets }, sessions.setsOldestFirst())
    }
}

/**
 * What a set logged, a session deleted or an exercise added changes, and nothing on the sheet does: the session
 * in progress, the sets [before] it, the [records] its sets set, the lifts done lately first, and the [exercises]
 * on offer.
 */
internal class Logged(
    val active: LiftSession?,
    val before: Before,
    val records: Set<Long>,
    val recent: List<Exercise>,
    val exercises: List<Exercise>,
) {
    companion object {
        fun of(before: Before, active: LiftSession?, made: List<CustomExercise>): Logged {
            val logged = active?.sets.orEmpty()
            val exercises = offeredExercises(made)
            return Logged(
                active,
                before,
                recordSetIds(before.all, logged),
                recentExercises(before.oldestFirst + logged, exercises),
                exercises,
            )
        }
    }
}

/** The user's gear as it stands, and the rest between sets. */
internal data class Gear(val owned: List<Double>, val bands: List<Band>, val bar: Double?, val restSeconds: Int)

/**
 * Everything the state is worked out from, as it last changed: [programDay] is the day to do next of the program
 * followed, and [programs] every program there is, the built-ins then the user's own.
 */
internal data class Inputs(
    val logged: Logged,
    val programDay: ProgramDay?,
    val programs: List<Program>,
    val gear: Gear,
    val draft: SetDraft,
    val editing: SetLog?,
    val finished: Finished?,
    val runs: Triple<EmomRun?, HoldTiming, DensityRun?>,
    val filters: ExerciseFilters,
) {
    /** Everything but the clocks, worked out again only when something changes, not every tick. */
    fun state(): SessionUiState {
        val active = logged.active
        val before = logged.before
        val plan = programDay(programDay, programs, active)
        val sets = active?.sets.orEmpty()
        val records = logged.records
        val owned = gear.owned
        val bands = gear.bands
        val bar = gear.bar
        val offered = offeredBands(bands)
        val (emom, timing, density) = runs
        return SessionUiState(
            session = active,
            bells = owned.ifEmpty { standardBells },
            ownsBells = owned.isNotEmpty(),
            bar = bar,
            bands = offered,
            draft = draft,
            free = active != null && plan == null,
            draftLastTime = if (plan == null) lastTime(before.oldestFirst, draft.exercise, draft.doubled) else null,
            upNext = plan?.let { upNext(it, sets, before.oldestFirst, bar, offered, draft, owned) },
            dayDone = plan != null && nextPlanned(plan.plannedSets(), sets) == null,
            plan = plan?.planRows(sets).orEmpty(),
            programDay = plan,
            programs = programs,
            followingDay = programDay,
            restSeconds = gear.restSeconds,
            emom = emom,
            density = density,
            hold = timing.run,
            otherSide = timing.offer?.takeIf { it.exercise == draft.exercise && it.side == draft.side }?.side,
            finished = finished,
            editing = editing,
            recordSetIds = records,
            latestRecord = sets.lastOrNull()?.takeIf { it.id in records },
            // The sheet never offers an archived exercise, whatever the library shows.
            library = LibraryList(
                logged.exercises,
                logged.recent,
                filters.copy(showArchived = false),
                ownedEquipment(owned, bands, bar),
            ),
        )
    }
}
