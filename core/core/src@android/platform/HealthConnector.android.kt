package dev.justinsmith.interval.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import co.touchlab.kermit.Logger

/**
 * Health Connect's permission sheet, for what the app asks (to write workouts, to read the weight), and
 * Play's page for the Health Connect app on a phone before Android 14 that lacks it. The sheet's answer
 * reaches [store] through [HealthStore.refresh], granted or not.
 */
@Composable
actual fun rememberHealthConnector(store: HealthStore): HealthConnector {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        store.refresh()
    }
    return remember(store, context) {
        object : HealthConnector {
            override fun connect() = ask.launch(HealthConnectStore.PERMISSIONS)

            override fun install() = openHealthConnectOnPlay(context)
        }
    }
}

/** Play's page for Health Connect, opened as its onboarding asks; a phone with no Play just stays put. */
private fun openHealthConnectOnPlay(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setPackage(PLAY_PACKAGE)
        data = Uri.parse(HEALTH_CONNECT_ON_PLAY)
        putExtra("overlay", true)
        putExtra("callerId", context.packageName)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Logger.withTag("HealthConnect").w(e) { "No Play Store to install Health Connect from" }
    }
}

private const val PLAY_PACKAGE = "com.android.vending"
private const val HEALTH_CONNECT_ON_PLAY =
    "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"
