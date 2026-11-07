package dev.justinsmith.interval.platform

import androidx.room.Room
import androidx.room.RoomDatabase
import co.touchlab.kermit.Logger
import dev.justinsmith.interval.data.AppDatabase
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

/** The interval side's services on the desktop. */
fun platformModule(): Module = module {
    single<PlatformStorage> { DesktopStorage() }
    single<CuePlayer> { DesktopCuePlayer() }.withOptions {
        onClose { it?.release() }
    }
    single<BackgroundWorkout> { NoBackgroundWorkout }
    single<PlanReminders> { NoPlanReminders }
}

/** A desktop has no notification centre the app could hand a day's reminder to. */
private object NoPlanReminders : PlanReminders {
    override suspend fun schedule(reminders: List<PlanReminder>) = Unit
}

/** A desktop window stays on screen, and its process keeps running behind other windows: nothing to keep. */
private object NoBackgroundWorkout : BackgroundWorkout {
    override fun start() = Unit

    override fun stop() = Unit
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

/**
 * [ToneSynth]'s tones through a Java Sound [Clip] each, opened from the tone as a WAV in memory the first time it
 * plays and kept; desktops have no speech engine or vibration to drive. A machine with no sound device logs the
 * failure and the cue goes unheard.
 */
private class DesktopCuePlayer : CuePlayer {
    private val log = Logger.withTag("DesktopCuePlayer")
    private val clips = mutableMapOf<Beep, Clip>()

    override fun beep(beep: Beep) {
        runCatching {
            val clip = clips.getOrPut(beep) {
                AudioSystem.getClip().apply {
                    open(AudioSystem.getAudioInputStream(ByteArrayInputStream(ToneSynth.wav(beep))))
                }
            }
            // A clip that has played to its end stays there: back to the start before it plays again.
            clip.stop()
            clip.framePosition = 0
            clip.start()
        }.onFailure { log.w(it) { "Beep failed" } }
    }

    override fun speak(speech: Speech) = Unit

    override fun vibrate(haptic: Haptic) = Unit

    override fun release() {
        clips.values.forEach { runCatching { it.close() } }
        clips.clear()
    }
}
