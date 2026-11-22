package dev.justinsmith.interval.platform

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android has no call for an app's icon, so the launcher's entry is one `<activity-alias>` an icon in the app's
 * manifest (`apps/androidApp`), each with its own icon over the one activity, and the icon picked is the alias
 * enabled. The kettlebell's is `.MainActivity`, enabled as installed: the name the activity itself had, so an
 * icon already on someone's Home Screen stays where it is through the update that brought the aliases.
 *
 * Disabling the entry the app was opened through ends its task, so the app closes a moment after a pick
 * ([closesApp]) though its process, and so a timer's service, lives on. A launcher lists the new icon in its own
 * time: the Pixel launcher changes one on the Home Screen where it is, another may take it off.
 */
class AndroidAppIconSwitcher(private val context: Context) : AppIconSwitcher {
    private val packages = context.packageManager
    private val chosen = MutableStateFlow(read())

    override val icon: StateFlow<AppIcon?> = chosen

    override val closesApp: Boolean = true

    override fun choose(icon: AppIcon) {
        if (chosen.value == null || chosen.value == icon) return
        // The one picked on before the other goes off, so the launcher never finds the app with no entry.
        runCatching {
            setEnabled(icon, true)
            AppIcon.entries.filter { it != icon }.forEach { setEnabled(it, false) }
        }
        chosen.value = read()
    }

    /** The icon whose alias is enabled; null in a build whose manifest has no aliases, where none is offered. */
    private fun read(): AppIcon? = runCatching {
        val ring = packages.getComponentEnabledSetting(alias(AppIcon.Ring))
        if (ring == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) AppIcon.Ring else AppIcon.Kettlebell
    }.getOrNull()

    private fun setEnabled(icon: AppIcon, enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        packages.setComponentEnabledSetting(alias(icon), state, PackageManager.DONT_KILL_APP)
    }

    private fun alias(icon: AppIcon) = ComponentName(
        context.packageName,
        when (icon) {
            AppIcon.Kettlebell -> KETTLEBELL_ALIAS
            AppIcon.Ring -> RING_ALIAS
        },
    )
}

// The aliases' names, as `apps/androidApp/src/AndroidManifest.xml` declares them.
private const val KETTLEBELL_ALIAS = "dev.justinsmith.interval.MainActivity"
private const val RING_ALIAS = "dev.justinsmith.interval.MainActivityRing"
