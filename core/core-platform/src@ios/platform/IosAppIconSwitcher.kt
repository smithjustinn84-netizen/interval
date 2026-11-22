package dev.justinsmith.interval.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.UIKit.UIApplication
import platform.UIKit.alternateIconName
import platform.UIKit.setAlternateIconName
import platform.UIKit.supportsAlternateIcons

/**
 * iOS's alternate icons: the kettlebell is the app's primary icon (`AppIcon` in the asset catalog, no alternate
 * name) and the ring the alternate one (`AppIconRing`, which the Xcode project lists in
 * `ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES`). iOS says the icon changed in an alert of its own. Built and
 * called on the main thread, as a screen does.
 */
class IosAppIconSwitcher : AppIconSwitcher {
    private val chosen = MutableStateFlow(read())

    override val icon: StateFlow<AppIcon?> = chosen

    override fun choose(icon: AppIcon) {
        val before = chosen.value
        if (before == null || before == icon) return
        chosen.value = icon
        val name = if (icon == AppIcon.Ring) RING_ICON_NAME else null
        // The handler may come on a queue of UIKit's own: a refusal puts back what was read before, asking nothing.
        UIApplication.sharedApplication.setAlternateIconName(name) { error ->
            if (error != null) chosen.value = before
        }
    }

    private fun read(): AppIcon? {
        val app = UIApplication.sharedApplication
        return when {
            !app.supportsAlternateIcons -> null
            app.alternateIconName == RING_ICON_NAME -> AppIcon.Ring
            else -> AppIcon.Kettlebell
        }
    }
}

/** The alternate icon set's name in `apps/iosApp/src/Assets.xcassets`. */
private const val RING_ICON_NAME = "AppIconRing"
