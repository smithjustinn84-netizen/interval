package dev.justinsmith.interval.history

/**
 * What is up next, as Today and History lead with it: the one thing to do now ([lead]), what else today holds
 * beside it ([also]), and what comes after ([later]).
 */
data class TodayAgenda(
    val lead: UpNextItem? = null,
    val also: List<UpNextItem> = emptyList(),
    val later: List<UpNextItem> = emptyList(),
) {
    /** Everything, in the order it is shown. */
    val all: List<UpNextItem> get() = listOfNotNull(lead) + also + later
}

/**
 * [upNext] as the day is led. On most days its first item leads and the rest come after. On a day with a sport
 * still to log, a class or a day on the wall, lifting stands back: the followed program's next day, which with no
 * plan of its own falls on every day, moves beside the lead rather than taking it. What leads then is what the user
 * planned for the day themself, a workout or a session on Lift, or with none the sport, to be logged; the day's
 * other sports sit beside it too. A session already under way is no stepping back from, and leads as ever. Once
 * the sport is logged it is up next no longer, and the day is led as any other.
 */
fun todayAgenda(upNext: List<UpNextItem>): TodayAgenda {
    val today = upNext.filter { it.day == HistoryDay.Today }
    val sports = today.filterIsInstance<UpNextItem.SportDay>()
    val first = upNext.firstOrNull()
    if (sports.isEmpty() || (first is UpNextItem.Lift && first.resume)) {
        return TodayAgenda(first, later = upNext.drop(1))
    }
    val standsBack = today.filter { it is UpNextItem.Lift && it.planId == null }
    val lead = today.firstOrNull { it !is UpNextItem.SportDay && it !in standsBack } ?: sports.first()
    val also = sports.filter { it != lead } + standsBack
    return TodayAgenda(lead, also, upNext.filter { it != lead && it !in also })
}
