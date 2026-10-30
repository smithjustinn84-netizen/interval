package dev.justinsmith.interval.platform

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.util.WeakHashMap

// `keepScreenOn` is one flag per view, so one count per view; weak, so a closed Activity's view can go.
private val awakeHolds = WeakHashMap<View, AwakeHolds>()

@Composable
actual fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        val holds = awakeHolds.getOrPut(view, ::AwakeHolds)
        if (holds.acquire()) view.keepScreenOn = true
        onDispose { if (holds.release()) view.keepScreenOn = false }
    }
}

@Composable
actual fun SystemBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !lightIcons
            isAppearanceLightNavigationBars = !lightIcons
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * "Remove animations" in Android's accessibility settings sets the animator duration scale to 0. Read again
 * each time the app comes back, so a change made in Settings meanwhile takes hold.
 */
@Composable
actual fun prefersReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var reduced by remember(resolver) { mutableStateOf(animationsRemoved(resolver)) }
    LifecycleResumeEffect(resolver) {
        reduced = animationsRemoved(resolver)
        onPauseOrDispose {}
    }
    return reduced
}

private fun animationsRemoved(resolver: ContentResolver) =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

actual val materialBackPreview: Boolean = true

actual val platformPageTransitions: Boolean = false
