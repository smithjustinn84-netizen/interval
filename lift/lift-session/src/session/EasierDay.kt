package dev.justinsmith.lift.session

import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.lift.data.GearStore
import dev.justinsmith.lift.data.IntervalActivity
import dev.justinsmith.lift.data.ProgramStore
import dev.justinsmith.lift.data.SessionStore
import dev.justinsmith.lift.data.SetStore
import dev.justinsmith.lift.data.snatchSets
import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.DEFAULT_BELL_KG
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramDay
import dev.justinsmith.lift.domain.Readiness
import dev.justinsmith.lift.domain.ReadinessCheck
import dev.justinsmith.lift.domain.ReadinessReason
import dev.justinsmith.lift.domain.SessionPlan
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.builtInPrograms
import dev.justinsmith.lift.domain.easier
import dev.justinsmith.lift.domain.gripLoad
import dev.justinsmith.lift.domain.lighterDayThan
import dev.justinsmith.lift.domain.offeredBands
import dev.justinsmith.lift.domain.plannedLoad
import dev.justinsmith.lift.domain.readiness
import dev.justinsmith.lift.domain.sets
import dev.justinsmith.lift.domain.standardBells
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * An easier day, offered on a taxed one ([Readiness.Taxed]): [reason] says why, and [lighterDay] names the program's
 * day lighter on the grip, when there is one, to do instead.
 */
data class EasierOffer(val reason: ReadinessReason, val lighterDay: Int? = null)

/**
 * The easier day a program day's session is offered before its first set, while its plan is as programmed: how
 * ready the lifter is comes from the sessions finished, the BJJ logged ([IntervalActivity.sportDays]) and the
 * grip, 15:15's snatches in it; [use] makes today's plan easier ([SessionPlan.easier]): grinds a bell down,
 * ballistics shorter, rests by tempo. Once used, or once a set is logged, it is offered no more.
 */
class EasierDay(
    private val sessionStore: SessionStore,
    private val setStore: SetStore,
    private val programStore: ProgramStore,
    private val gearStore: GearStore,
    private val intervals: IntervalActivity,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
) {
    /** The offer for the session in progress; null with none to make. */
    val offer: Flow<EasierOffer?> = combine(
        sessionStore.active,
        sessionStore.sessions,
        combine(intervals.sportDays, intervals.workouts, ::Pair),
        combine(programStore.programDay, programStore.customPrograms) { day, custom ->
            day to builtInPrograms + custom
        },
        gearStore.bells,
    ) { active, sessions, (sportDays, workouts), (day, programs), _ ->
        offerFor(
            dayToEase(active, day, programs),
            sessions,
            workouts.snatchSets.sets,
            sportDays.filter { it.grappling }.map { it.epochDay },
        )
    }

    /**
     * Whether today is a taxed day, by the same reading as the [offer]'s, whatever the session is and however far
     * through: what Lift's voice says "Stop a rep early" on.
     */
    val taxed: Flow<Boolean> = combine(
        sessionStore.sessions,
        intervals.sportDays,
        intervals.workouts,
    ) { sessions, sportDays, workouts ->
        check(sessions, workouts.snatchSets.sets, sportDays.filter { it.grappling }.map { it.epochDay })
            .readiness == Readiness.Taxed
    }

    /** Makes today's plan easier, as offered. */
    fun use() {
        setStore.changeSessionPlan { session ->
            val day = dayToEase(session, programStore.programDay.value, programStore.programs)
                ?: return@changeSessionPlan null
            eased(day, sessionStore.sessions.value)
        }
    }

    /** The offer for [today], the session's day to ease ([dayToEase]); null with none, or nothing to ease. */
    private fun offerFor(
        today: ProgramDay?,
        sessions: List<LiftSession>,
        alongside: List<SetLog>,
        grappling: List<Long>,
    ): EasierOffer? {
        if (today == null) return null
        val check = check(sessions, alongside, grappling)
        val reason = check.reason?.takeIf { check.readiness == Readiness.Taxed } ?: return null
        eased(today, sessions) ?: return null
        return EasierOffer(reason, today.program.lighterDayThan(today.number))
    }

    /** How ready the lifter is now, from the [sessions] finished, the sets [alongside] and the [grappling] days. */
    private fun check(sessions: List<LiftSession>, alongside: List<SetLog>, grappling: List<Long>): ReadinessCheck {
        val now = clock.nowMillis()
        val lifted = sessions.flatMap { it.sets } + alongside
        val grip = gripLoad(lifted, grappling, timestamps.dayNumber(now), timestamps::dayNumber)
        return readiness(sessions, grappling, grip, now, timestamps::dayNumber)
    }

    /** [session]'s program day, while its plan is as programmed and nothing is logged; else null. */
    private fun dayToEase(session: LiftSession?, day: ProgramDay?, programs: List<Program>): ProgramDay? =
        programDay(day, programs, session)?.takeIf { session?.plan == null && session?.sets.isNullOrEmpty() }

    /** [day]'s plan made easier, its loads as they would be suggested from the [sessions] before; null if unchanged. */
    private fun eased(day: ProgramDay, sessions: List<LiftSession>): SessionPlan? {
        val history = sessions.reversed().flatMap { it.sets }
        val bar = gearStore.bar.value
        val bands = offeredBands(gearStore.bands.value)
        return day.today.easier(
            loadOf = { block ->
                block.plannedSets().firstOrNull()
                    ?.let { plannedLoad(it, history, bar, bands, DEFAULT_BELL_KG, Band.Default) }
            },
            bells = gearStore.bells.value.ifEmpty { standardBells },
            pairs = gearStore.pairs.value,
            bar = bar,
            baseRest = setStore.restSeconds.value,
        )
    }
}
