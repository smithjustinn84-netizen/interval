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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
actual fun rememberPlatformModule(): Module = remember {
    module {
        single<PlatformStorage> { DesktopStorage() }
        single<CuePlayer> { DesktopCuePlayer() }
        single<TimestampFormatter> { JavaTimeFormatter() }
    }
}

/** Desktop displays follow the OS power settings; nothing to hold here. */
@Composable
actual fun KeepScreenOn() = Unit

/** Desktop windows have no system bars to restyle. */
@Composable
actual fun SystemBarIcons(lightIcons: Boolean) = Unit

actual val materialBackPreview: Boolean = false

actual fun epochMillisNow(): Long = System.currentTimeMillis()

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

private class JavaTimeFormatter : TimestampFormatter {
    // Day and month names follow the default locale; the time takes the locale's own short form.
    private val dateFormat = DateTimeFormatter.ofPattern("EEEE, MMM d")
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    override fun date(epochMillis: Long): String = dateFormat.format(zoned(epochMillis))

    override fun time(epochMillis: Long): String = timeFormat.format(zoned(epochMillis))

    override fun dayNumber(epochMillis: Long): Long = zoned(epochMillis).toLocalDate().toEpochDay()

    override fun weekday(epochMillis: Long): Int = zoned(epochMillis).dayOfWeek.value

    private fun zoned(epochMillis: Long) = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
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
