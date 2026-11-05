package dev.justinsmith.interval.ui

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.SceneStrategy

// What an entry says of itself in its metadata, which `Navigation.kt`'s transitions and the scenes read: an
// entry's key is private to it, so its kind and its group travel there.

/**
 * What kind of page an entry is, which decides how it moves. Pages one level deeper need no kind:
 * they are the default. An entry's key is private to it, so the kind travels in its metadata.
 */
enum class PageKind {
    /** A destination of the navigation bar or rail. */
    Tab,

    /** A mode of its own over the app (the workout), rather than a page one level deeper. */
    Mode,

    /** A page that opens beside the one under it on a wide window (Settings beside the Timer). */
    Beside,
}

/** The metadata marking an entry as [kind], for `entry<X>(metadata = …)`. */
fun pageKind(kind: PageKind): Map<String, Any> = mapOf(PAGE_KIND to kind)

internal const val PAGE_KIND = "dev.justinsmith.interval.pageKind"

/**
 * The metadata marking a tab's entry as one of group [name]: tabs the bar shows as one destination, each a
 * part of it picked on its page (Train's pillars). Moving between them swaps the page's content in place.
 */
fun tabGroup(name: String): Map<String, Any> = mapOf(TAB_GROUP to name)

internal const val TAB_GROUP = "dev.justinsmith.interval.tabGroup"

/** Whether this entry is a [PageKind.Tab]: a destination of the navigation bar or rail. */
internal val NavEntry<*>.isTab: Boolean get() = metadata[PAGE_KIND] == PageKind.Tab

/**
 * This strategy, asked only while a [PageKind.Beside] page is on top. Material's supporting-pane strategy
 * would also take the page under it on its own, as a scaffold scene with the same key as the pair's; opening
 * the pair would then be no change of scene, and nothing would move.
 */
fun <T : Any> SceneStrategy<T>.onlyBeside(): SceneStrategy<T> = SceneStrategy { entries ->
    if (entries.lastOrNull()?.metadata?.get(PAGE_KIND) == PageKind.Beside) {
        with(this@onlyBeside) { calculateScene(entries) }
    } else {
        null
    }
}
