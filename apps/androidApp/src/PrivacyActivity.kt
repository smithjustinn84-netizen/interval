package dev.justinsmith.interval

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.justinsmith.interval.about.PrivacyScreen
import dev.justinsmith.interval.ui.IntervalTheme

/**
 * The privacy policy on its own, as Health Connect opens it: from its permission sheet's "privacy policy"
 * link on Android 13 and before (`ACTION_SHOW_PERMISSIONS_RATIONALE`) and from the app's entry in its
 * settings on Android 14 and up (`VIEW_PERMISSION_USAGE`, through the alias in the manifest). It follows
 * the system's light or dark, having no container to read the app's own setting from. In the app the
 * same page is under About.
 */
class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            IntervalTheme {
                PrivacyScreen(onBack = ::finish)
            }
        }
    }
}
