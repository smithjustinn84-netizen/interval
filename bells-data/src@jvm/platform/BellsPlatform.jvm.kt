package dev.justinsmith.bells.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.justinsmith.bells.data.BellsDatabase
import org.koin.core.module.Module
import org.koin.dsl.module
import java.awt.Toolkit
import java.io.File

@Composable
actual fun rememberBellsPlatformModule(): Module = remember {
    module {
        single<RoomDatabase.Builder<BellsDatabase>> {
            Room.databaseBuilder<BellsDatabase>(name = appDataDirectory().resolve(BELLS_DATABASE).absolutePath)
        }
        // The system alert sound for every cue; desktops have nothing finer to drive.
        single<Beeper> { Beeper { runCatching { Toolkit.getDefaultToolkit().beep() } } }
        single<RestAlerts> { NoRestAlerts }
    }
}

/** A desktop window stays on screen and its process keeps running: nothing to keep awake or announce. */
private object NoRestAlerts : RestAlerts {
    override fun start(endAtEpochMillis: Long) = Unit

    override fun stop() = Unit
}

/** The OS's per-user app-data directory for the app, Interval's, created on first use. */
private fun appDataDirectory(): File {
    val home = File(System.getProperty("user.home"))
    val os = System.getProperty("os.name").lowercase()
    val base = when {
        "mac" in os -> home.resolve("Library/Application Support")
        "win" in os -> System.getenv("APPDATA")?.let(::File) ?: home.resolve("AppData/Roaming")
        else -> System.getenv("XDG_DATA_HOME")?.let(::File) ?: home.resolve(".local/share")
    }
    return base.resolve("interval").apply { mkdirs() }
}
