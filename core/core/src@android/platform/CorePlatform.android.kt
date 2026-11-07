package dev.justinsmith.interval.platform

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.koin.core.module.Module

/**
 * What both sides take from Android (Health Connect, the text-to-speech engine, the tones' audio tracks), over the application's [context]
 * (any context is narrowed to it).
 */
fun corePlatformModule(context: Context): Module = context.applicationContext.let { app ->
    sharedCorePlatformModule(
        appInfo = { AndroidAppInfo(app) },
        sharer = { AndroidSharer(app) },
        health = { HealthConnectStore(app) },
        speaker = { AndroidSpeaker(app) },
        tones = { AndroidTonePlayer() },
    )
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
