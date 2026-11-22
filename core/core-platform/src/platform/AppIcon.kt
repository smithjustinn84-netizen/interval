package dev.justinsmith.interval.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The icons the app can wear on the Home Screen: the rendered kettlebell every install starts on
 * (`tools/render_icon_3d.py`'s), and the flat mark, the workout's ring (`tools/generate_icons.py`'s).
 */
enum class AppIcon { Kettlebell, Ring }

/**
 * Which [AppIcon] is on the Home Screen, and the way to change it. The platform keeps the choice, so nothing of it
 * is stored or exported: Android's is which of the launcher's aliases is enabled, iOS's its alternate icon. Injected,
 * so tests record what was picked.
 */
interface AppIconSwitcher {
    /** The icon the app wears now; null where the platform keeps one icon (the desktop), and none is offered. */
    val icon: StateFlow<AppIcon?>

    /**
     * Whether picking an icon closes the app: Android's does, a moment after, since the app's task was started
     * through the launcher's entry the pick disables. A screen asks before it picks where this is so.
     */
    val closesApp: Boolean get() = false

    /** Puts [icon] on the Home Screen. Nothing where none is offered, or it is the one already there. */
    fun choose(icon: AppIcon)
}

/** The switcher of a platform with one icon: nothing to pick. */
object NoAppIconSwitcher : AppIconSwitcher {
    override val icon: StateFlow<AppIcon?> = MutableStateFlow(null)

    override fun choose(icon: AppIcon) = Unit
}
