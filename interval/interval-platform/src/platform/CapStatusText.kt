package dev.justinsmith.interval.platform

import dev.justinsmith.interval.platform.resources.Res
import dev.justinsmith.interval.platform.resources.notification_cap
import dev.justinsmith.interval.platform.resources.notification_cap_rounds
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

// What the notifications say of a time cap, the same on both platforms: the rounds done in it, in place of the round
// of so many, which a cap doesn't have.

/** The rounds done so far in a cap under way: "5 rounds done". */
internal suspend fun capRoundsText(rounds: Int): String =
    getPluralString(Res.plurals.notification_cap_rounds, rounds, rounds)

/** A cap ahead, whose rounds aren't known yet: "As many rounds as you can". */
internal suspend fun capAheadText(): String = getString(Res.string.notification_cap)
