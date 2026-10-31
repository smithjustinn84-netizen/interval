package dev.justinsmith.interval

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.justinsmith.interval.nav.Shortcut

class MainActivity : ComponentActivity() {
    /** A launcher shortcut to open, from [ShortcutActivity]; null once the app has opened it. */
    private var shortcut by mutableStateOf<Shortcut?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate: swaps the starting theme for `postSplashScreenTheme`. The intro's first
        // frame is this splash, drawn again in Compose, so drop the system one at once rather than
        // fading it over the intro.
        installSplashScreen().setOnExitAnimationListener { it.remove() }
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
        // Recreated (a rotation, a process restored), the shortcut was opened already: the back stack has it.
        if (savedInstanceState == null) shortcut = intent.shortcut()
        setContent {
            App(shortcut, onShortcutOpen = { shortcut = null })
        }
    }

    /** A shortcut tapped while the app runs: the same activity, brought forward. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.shortcut()?.let { shortcut = it }
    }

    // Reopened from Recents, the intent is the one that first started the task: not a shortcut tapped now.
    private fun Intent.shortcut(): Shortcut? =
        if (flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) {
            null
        } else {
            Shortcut.of(getStringExtra(ShortcutActivity.EXTRA_SHORTCUT))
        }
}
