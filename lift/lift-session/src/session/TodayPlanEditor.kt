package dev.justinsmith.lift.session

import dev.justinsmith.lift.data.GearStore
import dev.justinsmith.lift.data.ProgramStore
import dev.justinsmith.lift.data.SetStore
import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.Block
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.ProgramDay
import dev.justinsmith.lift.domain.SessionPlan
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.nextSet
import dev.justinsmith.lift.domain.offeredBands
import dev.justinsmith.lift.domain.planRows
import dev.justinsmith.lift.domain.plannedBand
import dev.justinsmith.lift.domain.standardBells
import dev.justinsmith.lift.domain.steppedBand
import dev.justinsmith.lift.domain.steppedKg
import dev.justinsmith.lift.domain.suggestedLoad

/**
 * Today's plan, on a program day: a block's load, rounds or reps changed, skipped or planned again, another lift
 * swapped in, the next set's load stepped. Each change is worked out in the write, from the session as stored
 * behind every write before it ([SetStore.changeSessionPlan]), so one made straight after another builds on it.
 */
class TodayPlanEditor(
    private val setStore: SetStore,
    private val programStore: ProgramStore,
    private val gearStore: GearStore,
) {
    /** Changes block [index] of today's plan by [transform]: its load, rounds or reps. */
    fun changeBlock(index: Int, transform: (Block) -> Block) {
        setStore.changeSessionPlan { session -> today(session)?.let { (plan, _) -> plan.change(index, transform) } }
    }

    /** Skips the rest of block [index] of today's plan: what comes next moves on. */
    fun skipBlock(index: Int) {
        setStore.changeSessionPlan { session ->
            today(session)?.let { (plan, done) -> plan.skip(index, done.getOrElse(index) { 0 }) }
        }
    }

    /** Plans block [index] of today's plan in full again. */
    fun unskipBlock(index: Int) {
        setStore.changeSessionPlan { session -> today(session)?.let { (plan, _) -> plan.unskip(index) } }
    }

    /** Swaps [exercise] in for the sets of block [index] still to do. */
    fun swapBlock(index: Int, exercise: Exercise) {
        setStore.changeSessionPlan { session ->
            today(session)?.let { (plan, done) ->
                val count = done.getOrElse(index) { 0 }
                if (plan.canSwap(index, count)) plan.swap(index, exercise, count) else null
            }
        }
    }

    /**
     * Steps the next set's load one [step] (−1 or +1): a bell, the bar's smallest pair of plates, or a band, as
     * suggested from the sets [before] the session (oldest first), the [draft]'s standing in where the lift has
     * neither a planned load nor a past. The rest of its block takes it, for today.
     */
    fun stepNextLoad(step: Int, before: List<SetLog>, draft: SetDraft) {
        setStore.changeSessionPlan { session ->
            val (plan, _) = today(session) ?: return@changeSessionPlan null
            val next = next(session, before, draft) ?: return@changeSessionPlan null
            val equipment = next.set.exercise.equipment
            if (equipment == Equipment.Band) {
                steppedBand(next.band, step, offeredBands(gearStore.bands.value))
                    ?.let { band -> plan.change(next.block) { it.copy(band = band.id) } }
            } else {
                val bells = gearStore.bells.value.ifEmpty { standardBells }
                steppedKg(equipment, next.bellKg, step, bells, gearStore.bar.value)
                    ?.let { kg -> plan.change(next.block) { it.copy(kg = kg) } }
            }
        }
    }

    /**
     * What is up next in [session] after the sets [before] it (oldest first), with the gear as it stands now, the
     * [draft] standing in as [upNext] says; null for none.
     */
    fun next(session: LiftSession?, before: List<SetLog>, draft: SetDraft): UpNext? {
        val day = session?.let(::dayOf) ?: return null
        return upNext(day, session.sets, before, gearStore.bar.value, offeredBands(gearStore.bands.value), draft)
    }

    /**
     * Today's plan in [session], as stored rather than as last drawn, and the sets of each block done; null in a
     * free session.
     */
    private fun today(session: LiftSession): Pair<SessionPlan, List<Int>>? {
        val day = dayOf(session) ?: return null
        return day.today to day.planRows(session.sets).map { it.done }
    }

    /** The program day [session] is, with today's plan as it has it; null in a free session. */
    private fun dayOf(session: LiftSession): ProgramDay? = programDay(programStore.programDay.value, session)
}

/** The program [day] that [session] is, with today's plan as [session] has it; null in a free session. */
internal fun programDay(day: ProgramDay?, session: LiftSession?): ProgramDay? {
    if (session?.program == null) return null
    return day?.takeIf { it.program.key == session.program }?.copy(plan = session.plan)
}

/**
 * What comes next on [day] after the sets [logged], with its load suggested from every set [before] this session
 * (oldest first) and those logged, the user's [bar] and [bands]: the [draft]'s bell and band stand in for a lift
 * with neither a planned load nor a past. Null once the day's sets are done.
 */
internal fun upNext(
    day: ProgramDay,
    logged: List<SetLog>,
    before: List<SetLog>,
    bar: Double?,
    bands: List<Band>,
    draft: SetDraft,
): UpNext? {
    val next = day.nextSet(logged) ?: return null
    val history = before + logged
    return UpNext(
        set = next.set,
        bellKg = suggestedLoad(next.set, history, bar, draft.bellKg),
        number = next.index + 1,
        total = next.total,
        emomMinutes = next.emomMinutes,
        band = plannedBand(next.set, history, bands, draft.band),
        block = next.block,
        lastTime = lastTime(before, next.set.exercise, next.set.double),
    )
}

/** The last set of [exercise], with two bells or one ([double]), among [before] (oldest first). */
internal fun lastTime(before: List<SetLog>, exercise: Exercise, double: Boolean): SetLog? =
    before.lastOrNull { it.exercise.key == exercise.key && it.double == double }
