package dev.justinsmith.interval.data

import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.days
import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.timer.weekStanding
import dev.justinsmith.lift.data.LiftActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * The week as it stands, for what shows it off screen (Android's home-screen widget): this week's workouts of
 * both kinds counted as History counts them, against the goal set on Plan, with the streak. Null until both
 * sides have read their storage, so nothing shows a zero from the defaults; then kept current on [scope] as a
 * workout is recorded, a session finished, the goal changed or the day turns (by [dayCheckMillis], the week
 * turning at Monday's midnight). `WorkoutRecorder` and `FinishSession` work the same standing out for a summary,
 * with the workout being finished counted before its write lands.
 */
class WeekStandingSource(
    repository: IntervalRepository,
    lift: LiftActivity,
    timestamps: TimestampFormatter,
    clock: WallClock,
    scope: CoroutineScope,
    dayCheckMillis: Long = DAY_CHECK_MILLIS,
) {
    val standing: StateFlow<WeekStanding?> = flow {
        repository.ready.first { it }
        lift.ready.first { it }
        emitAll(
            combine(
                repository.history.history,
                lift.sessions,
                repository.settings.appSettings,
                clock.days(timestamps, dayCheckMillis),
            ) { history, sessions, app, today ->
                val days = history.map { timestamps.dayNumber(it.finishedAtEpochMillis) } +
                    sessions.mapNotNull { it.finishedAtEpochMillis }.map(timestamps::dayNumber)
                weekStanding(days, today, app.weeklyGoal)
            },
        )
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, null)
}
