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
 * today's included. Its streak is [weeksMeetingGoal]'s, as History's is, so the two agree.
 */
fun weekStanding(workoutDays: List<Long>, today: Long, goal: Int?): WeekStanding {
    val byWeek = workoutDays.groupingBy { weekStartOf(it) }.eachCount()
    val current = weekStartOf(today)
    val streak = goal?.let { weeksMeetingGoal(current, it) { week -> byWeek[week] ?: 0 } } ?: 0
    return WeekStanding(byWeek[current] ?: 0, goal, streak)
}

/**
 * The weeks in a row that have held at least [goal] workouts, by [countInWeek] (a week's Monday to its
 * workouts), counting back from the week starting [current]. This week counts once it has met the goal;
 * until then the run counts back from last week, so a week still under way doesn't break it.
 */
fun weeksMeetingGoal(current: Long, goal: Int, countInWeek: (weekStart: Long) -> Int): Int {
    if (goal <= 0) return 0
    var week = if (countInWeek(current) >= goal) current else current - DAYS_PER_WEEK
    var run = 0
    while (countInWeek(week) >= goal) {
        run++
        week -= DAYS_PER_WEEK
    }
    return run
}
