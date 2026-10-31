package dev.justinsmith.interval.platform

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.koin.core.module.Module

@Composable
actual fun rememberCorePlatformModule(): Module {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        corePlatformModule(appInfo = { AndroidAppInfo(context) }, sharer = { AndroidSharer(context) })
    }
}

/** The `versionName` of the installed package: `versionName` in `apps/androidApp/module.yaml`. */
private class AndroidAppInfo(context: Context) : AppInfo {
    override val version: String? = runCatching {
        val manager = context.packageManager
        val name = context.packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPackageInfo(name, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            manager.getPackageInfo(name, 0)
        }.versionName
    }.getOrNull()
}
