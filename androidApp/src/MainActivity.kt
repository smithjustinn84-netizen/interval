package dev.justinsmith.interval

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
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
        setContent {
            App()
        }
    }
}
