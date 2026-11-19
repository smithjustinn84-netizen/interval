package dev.justinsmith.interval.platform

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Android 12 and up: the two permissions to find and to connect to nearby devices, asked together on one prompt
 * when they aren't both granted. Refused twice the system stops showing it and answers no at once, which the
 * caller says as it would any other failure to reach the sensor.
 */
@Composable
actual fun rememberBluetoothAccess(): BluetoothAccess {
    val context = LocalContext.current
    var waiting by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { answers ->
        waiting?.invoke(answers.values.all { it })
        waiting = null
    }
    return remember(context, ask) {
        BluetoothAccess { then ->
            val wanted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                emptyArray()
            }
            val granted = wanted.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            if (granted) {
                then(true)
            } else {
                waiting = then
                ask.launch(wanted)
            }
        }
    }
}
