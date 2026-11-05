package dev.justinsmith.interval.nav

/**
 * Where a launcher shortcut opens the app (a long-press on the icon: Android's shortcuts, iOS's quick actions),
 * each over its own tab, so Back lands there. [id] is what the shortcut's intent carries, and a quick action's type.
 */
enum class Shortcut(val id: String) {
    /** A workout of what the Timer holds, as its Start begins one, over the Timer. */
    StartWorkout("start-workout"),

    /** Lift's session, started or resumed as the Lift tab's button does, over the Lift tab. */
    LiftSession("lift-session"),
    ;

    companion object {
        /** The shortcut an intent's [id] names; null for none or one this version doesn't know. */
        fun of(id: String?): Shortcut? = entries.firstOrNull { it.id == id }
    }
}
