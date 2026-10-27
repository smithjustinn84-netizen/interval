package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.justinsmith.interval.data.AppDatabase
import org.koin.core.module.Module
import org.koin.dsl.module
import java.awt.Toolkit
import java.io.File

@Composable
actual fun rememberPlatformModule(): Module = remember {
    module {
        single<PlatformStorage> { DesktopStorage() }
        single<CuePlayer> { DesktopCuePlayer() }
        single<TimestampFormatter> { systemTimestampFormatter() }
        single<AppInfo> { DesktopAppInfo() }
        single<BackgroundWorkout> { NoBackgroundWorkout }
        single<PlanReminders> { NoPlanReminders }
        single<Sharer> { DesktopSharer() }
    }
}

/** A desktop has no notification centre the app could hand a day's reminder to. */
private object NoPlanReminders : PlanReminders {
    override suspend fun schedule(reminders: List<PlanReminder>) = Unit
}

/** A desktop window stays on screen, and its process keeps running behind other windows: nothing to keep. */
private object NoBackgroundWorkout : BackgroundWorkout {
    override fun start(controls: WorkoutControls) = Unit

    override fun update(status: WorkoutStatus) = Unit

    override fun stop() = Unit
}

@Composable
actual fun AskToShowNotifications() = Unit

/** jpackage stamps the version into a packaged app; a run from source carries none. */
private class DesktopAppInfo : AppInfo {
    override val version: String? = System.getProperty("jpackage.app-version")
}

/** The OS's per-user app-data directory, created on first use. */
private class DesktopStorage : PlatformStorage {
    private val directory: File = appDataDirectory().resolve("interval").apply { mkdirs() }

    override fun databaseBuilder(): RoomDatabase.Builder<AppDatabase> =
        Room.databaseBuilder<AppDatabase>(name = directory.resolve(DATABASE_NAME).absolutePath)

    override val preferencesPath: String = directory.resolve(PREFERENCES_NAME).absolutePath

    private fun appDataDirectory(): File {
        val home = File(System.getProperty("user.home"))
        val os = System.getProperty("os.name").lowercase()
        return when {
            "mac" in os -> home.resolve("Library/Application Support")
            "win" in os -> System.getenv("APPDATA")?.let(::File) ?: home.resolve("AppData/Roaming")
            else -> System.getenv("XDG_DATA_HOME")?.let(::File) ?: home.resolve(".local/share")
        }
    }
}

/** The system alert beep for every sound; desktops have no speech engine or vibration to drive. */
private class DesktopCuePlayer : CuePlayer {
    override fun beep(beep: Beep) {
        runCatching { Toolkit.getDefaultToolkit().beep() }
    }

    override fun speak(speech: Speech) = Unit

    override fun vibrate(haptic: Haptic) = Unit

    override fun release() = Unit
}
