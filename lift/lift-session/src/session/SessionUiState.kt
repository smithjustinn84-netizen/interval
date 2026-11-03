package dev.justinsmith.lift.session

import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.Block
import dev.justinsmith.lift.domain.EmomState
import dev.justinsmith.lift.domain.HoldState
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.PersonalRecord
import dev.justinsmith.lift.domain.PlanChanges
import dev.justinsmith.lift.domain.PlanRow
import dev.justinsmith.lift.domain.PlannedSet
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramDay
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.RestState
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.domain.bands
import dev.justinsmith.lift.domain.offeredBands
import dev.justinsmith.lift.domain.offeredExercises
import dev.justinsmith.lift.domain.recordSetIds
import dev.justinsmith.lift.domain.standardBells
import dev.justinsmith.lift.library.ExerciseDetailUiState
import dev.justinsmith.lift.library.LibraryList

/**
 * A session just finished, for the summary: it as saved and the records it set; for a program day changed,
 * the [changes] to offer to keep, the names the user's programs already have, and once kept, the program they
 * were [kept] in.
 */
data class Finished(
    val session: LiftSession,
    val records: List<PersonalRecord>,
    /** Where the week stands with this session in it. */
    val week: WeekStanding? = null,
    val changes: PlanChanges? = null,
    val taken: Set<String> = emptySet(),
    val kept: ProgramKey? = null,
    /** The user's bands, to name a band a change plans by. */
    val bands: List<Band> = emptyList(),
    /** The program this session finished, when it was its last day. */
    val lastDayOf: Program? = null,
    /** Its blocks done in full at the top of their range, with no heavier load to go to. */
    val toppedOut: List<Block> = emptyList(),
    /** The summary's note as typed; [session] holds it as kept, trimmed. */
    val note: String = "",
)

/**
 * The plan's next set: which of [total] it is, with its [load] as suggested, and whether it opens an EMOM of
 * [emomMinutes].
 */
data class UpNext(
    val set: PlannedSet,
    val load: Load,
    val number: Int,
    val total: Int,
    val emomMinutes: Int?,
    /** Which of the day's blocks it is in, from 0. */
    val block: Int = 0,
    /** The last set of the lift in a session before this one, to go by; null the first time. */
    val lastTime: SetLog? = null,
)

/**
 * The session's clocks, which move every tick while a timer runs and every second besides. Apart from
 * [SessionUiState], so only what draws them recomposes as they move.
 */
data class SessionClocks(
    /** How long the session has run: "14:32". */
    val elapsed: String = formatClock(0),
    /** Since the last set, for resting: "0:38"; null before the first. */
    val sinceLast: String? = null,
    /** The rest counting down after a set; null when none is. */
    val rest: RestState? = null,
    val emomState: EmomState? = null,
    val holdState: HoldState? = null,
)

/** The [session]'s clocks [now], and its [timing]'s. */
internal fun clocksAt(session: LiftSession?, now: Long, timing: TimerState) = SessionClocks(
    elapsed = formatClock(session?.elapsedSeconds(now) ?: 0),
    sinceLast = session?.sets?.lastOrNull()?.let {
        formatClock(((now - it.loggedAtEpochMillis) / MILLIS_PER_SECOND).toInt())
    },
    rest = timing.restState,
    emomState = timing.emomState,
    holdState = timing.holdState,
)

private const val MILLIS_PER_SECOND = 1_000L

data class SessionUiState(
    val session: LiftSession? = null,
    /**
     * Whether the screen asks to show notifications, for the rest's off screen: from the session's first rest on,
     * and staying so, so it asks once rather than at each rest.
     */
    val asksToShowNotifications: Boolean = false,
    /** The bells the sheet offers: the user's own, or every standard size until they have said. */
    val bells: List<Double> = standardBells,
    val ownsBells: Boolean = false,
    /** What the user's barbell weighs empty; null until they have said. */
    val bar: Double? = null,
    /** The bands the sheet offers, lightest first. */
    val bands: List<Band> = offeredBands(bands(emptyList())),
    val draft: SetDraft = SetDraft(),
    /** Whether no program day lays the session out: the Next set card composes [draft]. */
    val free: Boolean = false,
    /**
     * In a free session: the last set of [draft]'s lift in a session before this one, to go by; null the
     * first time.
     */
    val draftLastTime: SetLog? = null,
    /** For a program day: what comes next, or null once its sets are done ([dayDone]). */
    val upNext: UpNext? = null,
    val dayDone: Boolean = false,
    /** For a program day: its blocks as today's plan has them. */
    val plan: List<PlanRow> = emptyList(),
    /** The program day the session is, null for a free one: what names it to the health store. */
    val programDay: ProgramDay? = null,
    val emom: EmomRun? = null,
    /** A hold being timed on the Log sheet. */
    val hold: HoldRun? = null,
    /** The side to time next, for the same length, after the first side of a one-sided hold. */
    val otherSide: Side? = null,
    val finished: Finished? = null,
    /** A set logged, open on the Log sheet to change: the sheet's draft is it until saved or closed. */
    val editing: SetLog? = null,
    /** The sets of this session that beat a record standing as they were logged: see [recordSetIds]. */
    val recordSetIds: Set<Long> = emptySet(),
    /** The last set logged, when it is one of them: the one to announce. */
    val latestRecord: SetLog? = null,
    /** What the sheet's picker offers, the ones done lately first, and how it is narrowed. */
    val library: LibraryList = LibraryList(offeredExercises(emptyList())),
    /** The exercise whose page is open, and the user's history with it. */
    val detail: ExerciseDetailUiState? = null,
)
