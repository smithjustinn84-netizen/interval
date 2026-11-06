package dev.justinsmith.interval.export

import dev.justinsmith.interval.model.LoggedSport
import dev.justinsmith.interval.model.Sport

// A sport in the file: by name rather than by what the database calls it, as the kinds of Lift session are.

private const val BJJ = "Bjj"
private const val CLIMBING = "Climbing"
private const val RUNNING = "Running"
private const val CUSTOM = "Custom"

internal val Sport.exportKey: String
    get() = when (this) {
        Sport.Bjj -> BJJ
        Sport.Climbing -> CLIMBING
        Sport.Running -> RUNNING
        is Sport.Custom -> CUSTOM
    }

/** The user's own name for a sport they named; null for the rest. */
internal val Sport.exportName: String? get() = (this as? Sport.Custom)?.name

/** The sport a file's [key] and [name] hold; one this build doesn't know makes the file unreadable. */
internal fun sportNamed(key: String, name: String?): Sport = when (key) {
    BJJ -> Sport.Bjj
    CLIMBING -> Sport.Climbing
    RUNNING -> Sport.Running
    CUSTOM -> requireNotNull(Sport.custom(name.orEmpty())) { "sport name" }
    else -> throw IllegalArgumentException("sport")
}

internal fun LoggedSport.export() = SportDayExport(epochDay, sport.exportKey, sport.exportName, loggedAtEpochMillis)

internal fun SportDayExport.toDomain() = LoggedSport(0, sportNamed(sport, name), epochDay, loggedAtEpochMillis)

/** What makes two logged sport days the same one: the day and the sport. */
internal val LoggedSport.key get() = epochDay to sport
