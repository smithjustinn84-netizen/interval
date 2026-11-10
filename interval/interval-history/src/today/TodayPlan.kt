package dev.justinsmith.interval.today

import dev.justinsmith.interval.history.HistoryDay
import dev.justinsmith.interval.history.TodayAgenda
import dev.justinsmith.interval.history.TrainingDay
import dev.justinsmith.interval.history.UpNextItem
import dev.justinsmith.interval.history.WorkoutKind
import dev.justinsmith.interval.history.upNextKind
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.lift.domain.ReadinessReason

// Today as a coach lays the day out: what is done, what is still to do and in what order, and what can wait.

/** Something done today, a tick on Today's card: oldest first, the sports logged after the workouts. */
sealed interface DoneStep {
    /** An interval workout or a flow: its [name] where it has one, else worded by its [config]; how long it ran. */
    data class Workout(val name: String?, val config: IntervalConfig, val elapsedSeconds: Int) : DoneStep

    /** A session on Lift, by its [title], blank for one with none. */
    data class Session(val title: String, val elapsedSeconds: Int) : DoneStep

    /** A day of [sport], logged. */
    data class SportLogged(val sport: Sport) : DoneStep
}

/**
 * The day in order. [done] is ticked off. [steps] are what today still holds, the first of them the one thing to
 * do now, under the tab's one raised Start (or its Log it). [next] is what comes after, with a quiet button: the
 * next day's, or the program's day [waiting] behind a class already logged, which today no longer asks for. After it
 * come [later]'s.
 */
data class TodayPlan(
    val done: List<DoneStep> = emptyList(),
    val steps: List<UpNextItem> = emptyList(),
    val next: UpNextItem? = null,
    val later: List<UpNextItem> = emptyList(),
) {
    /** [next] is today's all the same: the program's day, stood back for good behind the class logged. */
    val waiting: Boolean get() = next?.day == HistoryDay.Today
}

/**
 * [agenda] as the day's steps, with what is [done] today. Everything of today's is a step, in the agenda's order:
 * what leads, what stands beside it, then anything else planned today. Once a sport is logged today the program's
 * unplanned day, which stood back for the class, stands back still: with nothing else left it is [TodayPlan.next],
 * to do if there is time, and the day is done. A session under way is never stood back.
 */
fun todayPlan(agenda: TodayAgenda, done: List<DoneStep>): TodayPlan {
    val all = agenda.all
    val today = all.filter { it.day == HistoryDay.Today }
    val after = all.filter { it.day != HistoryDay.Today }
    val sportDone = done.any { it is DoneStep.SportLogged }
    val standsBack = today.all { it is UpNextItem.Lift && it.planId == null && !it.resume }
    return if (sportDone && today.isNotEmpty() && standsBack) {
        TodayPlan(done, next = today.first(), later = today.drop(1) + after)
    } else {
        TodayPlan(done, today, after.firstOrNull(), after.drop(1))
    }
}

/** Whether [item], a step after the first on a day with a sport, is the program's day stood back: for spare time. */
fun TodayPlan.spare(item: UpNextItem): Boolean =
    item is UpNextItem.Lift && item.planId == null && !item.resume && item != steps.firstOrNull() &&
        steps.any { it is UpNextItem.SportDay }

/** What this day has done, as Today ticks it: its workouts as [nameOf] names each, its sessions, its sports. */
internal fun TrainingDay.doneSteps(nameOf: (IntervalConfig) -> String?): List<DoneStep> {
    val timed = workouts.map {
        it.finishedAtEpochMillis to DoneStep.Workout(nameOf(it.config), it.config, it.elapsedSeconds)
    } + liftWorkouts.map { it.finishedAtEpochMillis to DoneStep.Session(it.title, it.elapsedSeconds) }
    return timed.sortedBy { it.first }.map { it.second } + sports.map { DoneStep.SportLogged(it.sport) }
}

/** The kind of day today is, which the line over the day says: by what it holds, done once nothing is left. */
sealed interface DayKind {
    /** A day of [sport], still to log. */
    data class SportDay(val sport: Sport) : DayKind

    data object Strength : DayKind

    data object Conditioning : DayKind

    data object Mobility : DayKind

    /** Nothing asked of today, and nothing done. */
    data object Rest : DayKind

    /** Everything today asked for is done. */
    data object Done : DayKind
}

/** What the coach says of the day, under its [DayKind]: at most one of these, the first that holds. */
sealed interface CoachNote {
    /** A day to take easier, and why. */
    data class Taxed(val reason: ReadinessReason) : CoachNote

    /** A day of [sport]; [flowFirst] when the flow to loosen up leads it. */
    data class ForSport(val sport: Sport, val flowFirst: Boolean) : CoachNote

    /** The day is done; [goalMet] when the week's goal is too. */
    data class DayDone(val goalMet: Boolean) : CoachNote

    /** A day with nothing planned, in a week that has plans. */
    data object RestDay : CoachNote
}

/** The kind of day this plan makes today. */
val TodayPlan.kind: DayKind
    get() {
        val sport = steps.firstNotNullOfOrNull { (it as? UpNextItem.SportDay)?.sport }
        return when {
            sport != null -> DayKind.SportDay(sport)

            steps.isEmpty() -> if (done.isEmpty()) DayKind.Rest else DayKind.Done

            else -> when (upNextKind(steps.first())) {
                WorkoutKind.Strength -> DayKind.Strength
                WorkoutKind.Mobility -> DayKind.Mobility
                else -> DayKind.Conditioning
            }
        }
    }

/**
 * What there is to say of the day, or null with nothing worth a line: that it is one to take easier ([taxed], while
 * something to train leads it), that it is a sport's, that it is done ([goalMet] with the week's goal), or that it
 * is a rest day (only [resting], in a week with something still to come).
 */
fun TodayPlan.note(taxed: ReadinessReason?, goalMet: Boolean): CoachNote? {
    val lead = steps.firstOrNull()
    val sport = steps.firstNotNullOfOrNull { (it as? UpNextItem.SportDay)?.sport }
    return when {
        taxed != null && lead != null && lead !is UpNextItem.SportDay && lead !is UpNextItem.Flow ->
            CoachNote.Taxed(taxed)

        sport != null -> CoachNote.ForSport(sport, flowFirst = lead is UpNextItem.Flow)

        steps.isEmpty() && done.isNotEmpty() -> CoachNote.DayDone(goalMet)

        resting -> CoachNote.RestDay

        else -> null
    }
}

/** A day that has its answer, a plan, a tick or a rest: the picks by time fold to a row under it. */
val TodayPlan.settled: Boolean get() = steps.isNotEmpty() || done.isNotEmpty() || resting

/** A rest day: nothing asked of today and nothing done, with something still to come. */
val TodayPlan.resting: Boolean get() = steps.isEmpty() && done.isEmpty() && next != null && !waiting
