package dev.justinsmith.interval.timer

/**
 * [count] of the [free] weekdays (1 for Monday), as evenly round the week as they go: the days whose gaps, the
 * week taken as a circle, differ least, and of two as even the earlier. Three of a whole week are Monday, Wednesday
 * and Friday. A day [including] is always among them, and "earlier" counts from it, so three from Wednesday are
 * Wednesday, Friday and Sunday; with it, at least that day is given back.
 */
fun spreadWeekdays(free: Set<Int>, count: Int, including: Int? = null): Set<Int> {
    val week = 1..DAYS_PER_WEEK
    val pinned = including?.takeIf { it in week }
    val start = pinned ?: 1
    val days = (free + setOfNotNull(pinned)).filter { it in week }
        .sortedBy { (it - start + DAYS_PER_WEEK) % DAYS_PER_WEEK }
    val wanted = if (pinned == null) count else count.coerceAtLeast(1)
    return when {
        wanted <= 0 || days.isEmpty() -> emptySet()
        wanted >= days.size -> days.toSet()
        pinned == null -> combinations(days, wanted).minByOrNull(::unevenness).orEmpty().toSet()
        else -> combinations(days.drop(1), wanted - 1).map { listOf(pinned) + it }.minByOrNull(::unevenness)
            .orEmpty().toSet()
    }
}

/** The sum of the squared gaps between [days], in order round the week: least when they are evenly apart. */
private fun unevenness(days: List<Int>): Int = days.indices.sumOf { index ->
    val gap = (days[(index + 1) % days.size] - days[index] + DAYS_PER_WEEK - 1) % DAYS_PER_WEEK + 1
    gap * gap
}

/** Every [count] of [days], each in their order and the first of them first. */
private fun combinations(days: List<Int>, count: Int): List<List<Int>> = when {
    count == 0 -> listOf(emptyList())
    days.size < count -> emptyList()
    else -> combinations(days.drop(1), count - 1).map { listOf(days.first()) + it } + combinations(days.drop(1), count)
}
