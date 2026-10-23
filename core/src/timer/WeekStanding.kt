package dev.justinsmith.interval.timer

/**
 * Where the week stands once a workout is in: how many workouts it holds ([count]), the weekly [goal] if
 * one is set, and the weeks in a row that have met it ([streak]), this one included once it does.
 */
data class WeekStanding(val count: Int, val goal: Int?, val streak: Int) {
    /** The goal is set and this week has reached it. */
    val goalMet: Boolean get() = goal != null && count >= goal

    /** This workout is the one that brought the week to its goal: the moment to mark. */
    val justMet: Boolean get() = goal != null && count == goal
}

/**
 * The standing of the week holding [today], over [workoutDays]: the epoch day of every workout done,
 * today's included. The streak counts back from this week once it has met the goal, else from last
 * week, as History's streak does, so the two agree.
 */
fun weekStanding(workoutDays: List<Long>, today: Long, goal: Int?): WeekStanding {
    val byWeek = workoutDays.groupingBy { weekStartOf(it) }.eachCount()
    val current = weekStartOf(today)
    val count = byWeek[current] ?: 0
    var streak = 0
    if (goal != null && goal > 0) {
        var week = if (count >= goal) current else current - DAYS_PER_WEEK
        while ((byWeek[week] ?: 0) >= goal) {
            streak++
            week -= DAYS_PER_WEEK
        }
    }
    return WeekStanding(count, goal, streak)
}
