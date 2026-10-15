package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.justinsmith.interval.data.KeyValueStore
import org.koin.core.module.Module
import org.koin.dsl.module
import java.awt.Toolkit
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.prefs.Preferences

@Composable
actual fun rememberPlatformModule(): Module = remember {
    module {
        single<KeyValueStore> { PreferencesStore() }
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

actual fun epochMillisNow(): Long = System.currentTimeMillis()

private class PreferencesStore : KeyValueStore {
    private val prefs = Preferences.userRoot().node("dev/justinsmith/interval")

    override fun getString(key: String): String? = prefs.get(key, null)

    override fun putString(key: String, value: String) {
        prefs.put(key, value)
    }
}

private class JavaTimeFormatter : TimestampFormatter {
    private val dayFormat = DateTimeFormatter.ofPattern("EEEE, MMM d")
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a")

    override fun day(epochMillis: Long, nowMillis: Long): String {
        val date = zoned(epochMillis).toLocalDate()
        val today = zoned(nowMillis).toLocalDate()
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> dayFormat.format(date)
        }
    }

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

    override fun speak(text: String) = Unit

    override fun vibrate(haptic: Haptic) = Unit

    override fun release() = Unit
}
