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

actual fun epochMillisNow(): Long = System.currentTimeMillis()

private class PreferencesStore : KeyValueStore {
    private val prefs = Preferences.userRoot().node("dev/justinsmith/interval")

    override fun getString(key: String): String? = prefs.get(key, null)

    override fun putString(key: String, value: String) {
        prefs.put(key, value)
    }
}

private class JavaTimeFormatter : TimestampFormatter {
    private val formatter = DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a")

    override fun format(epochMillis: Long): String =
        formatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
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
