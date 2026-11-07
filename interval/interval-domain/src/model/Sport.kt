package dev.justinsmith.interval.model

/**
 * A sport trained away from the app, a class or a day on the wall, which the plan and the week show beside the
 * workouts done here. The three named here are worded by the screens; [Custom] is one the user named.
 */
sealed interface Sport {
    data object Bjj : Sport

    data object Climbing : Sport

    data object Running : Sport

    /** One the user named: [name] is theirs as typed, trimmed and never blank ([custom]). */
    data class Custom(val name: String) : Sport

    companion object {
        const val NAME_MAX_LENGTH = 24

        /** The sports offered by name, in the order they are listed. */
        val named: List<Sport> = listOf(Bjj, Climbing, Running)

        /** The place after [named] in a list of them: the sport the user names themself. */
        val OTHER: Int = named.size

        /**
         * The sport picked from a list of [named] then [OTHER]: one of them by [index], or at [OTHER] the one [typed]
         * names, null while it names none or the pick is past both.
         */
        fun picked(index: Int, typed: String): Sport? = named.getOrNull(index) ?: custom(typed).takeIf { index == OTHER }

        /** The sport [typed] names, or null while it names none. */
        fun custom(typed: String): Custom? = typed.trim().take(NAME_MAX_LENGTH).trim().ifEmpty { null }?.let(::Custom)
    }
}

private const val SHORT_NAME_LENGTH = 5

/** [name] short enough to sit under a day of the week: its first word, at most five letters of it. */
fun shortSportName(name: String): String = name.trim().substringBefore(' ').take(SHORT_NAME_LENGTH)

/**
 * A day of [sport] the user logged, on [epochDay] (see `CalendarDate`): the day itself rather than a moment, since
 * one logged later still belongs to the day it was trained on. It has no length; it counts as one workout that day.
 */
data class LoggedSport(val id: Long, val sport: Sport, val epochDay: Long, val loggedAtEpochMillis: Long)
