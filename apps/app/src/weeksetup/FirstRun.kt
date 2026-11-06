package dev.justinsmith.interval.weeksetup

import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.lift.data.LiftRepository

// Whether the app, opening, is a new install's first plain open: the one time Set up opens by itself.

/**
 * Whether this open is Set up's turn: the app [offers] it (the app itself does; a test only when that is what it
 * is about), it was opened plainly rather than [byShortcut], which says where to go, and Set up has not had its
 * turn before ([offered]). The turn is taken whether or not Set up then opens.
 */
internal fun isSetupTurn(offers: Boolean, byShortcut: Boolean, offered: Boolean): Boolean =
    offers && !byShortcut && !offered

/**
 * Whether the app should open on Set up: it is its [turn] and there is [nothing] in the app for it to get in the
 * way of. So it opens once on a new install, and never for someone who had the app before Set up was in it.
 */
internal fun opensOnSetup(turn: Boolean, nothing: Boolean): Boolean = turn && nothing

/**
 * Whether nothing has been done in the app yet, on either side: no workout or sport day logged, nothing planned,
 * no preset saved, no goal set, no session on Lift, no program followed and no bell owned. Read once both
 * stores are ready.
 */
internal fun nothingYet(intervals: IntervalRepository, lift: LiftRepository): Boolean =
    intervals.history.history.value.isEmpty() &&
        intervals.history.sportDays.value.isEmpty() &&
        intervals.plans.planned.value.isEmpty() &&
        intervals.presets.customPresets.value.isEmpty() &&
        intervals.settings.appSettings.value.weeklyGoal == null &&
        nothingOnLift(lift)

private fun nothingOnLift(lift: LiftRepository): Boolean =
    lift.sessions.sessions.value.isEmpty() &&
        lift.sessions.active.value == null &&
        lift.programs.followed.value == null &&
        lift.gear.bells.value.isEmpty()
