package dev.justinsmith.bells.platform

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.justinsmith.bells.data.BellsDatabase
import dev.justinsmith.bells.domain.EmomCue
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

@Composable
actual fun rememberBellsPlatformModule(): Module = LocalContext.current.applicationContext.let { context ->
    remember(context) {
        module {
            single<RoomDatabase.Builder<BellsDatabase>> {
                Room.databaseBuilder<BellsDatabase>(context, context.getDatabasePath(BELLS_DATABASE).absolutePath)
            }
            single<Beeper> { ToneBeeper() }.withOptions {
                onClose { it?.release() }
            }
            single<RestAlerts> { AndroidRestAlerts(context) }
        }
    }
}

/**
 * Tones on STREAM_MUSIC, so beeps follow the media volume and mix over music, as Interval's do. The generator
 * holds an audio track, one of a few the system has and native memory the GC never weighs, so it is let go
 * with the container ([release]) rather than left to a finaliser: the container is built again with each
 * Activity, and each would otherwise leave a track behind.
 */
private class ToneBeeper : Beeper {
    private val tones: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
    }.getOrNull()

    override fun beep(cue: EmomCue) {
        val (tone, millis) = when (cue) {
            EmomCue.Countdown -> ToneGenerator.TONE_PROP_BEEP to SHORT_MILLIS
            EmomCue.Start -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD to LONG_MILLIS
            EmomCue.Finished -> ToneGenerator.TONE_PROP_BEEP2 to LONG_MILLIS
        }
        runCatching { tones?.startTone(tone, millis) }
    }

    override fun release() {
        runCatching { tones?.release() }
    }

    private companion object {
        const val TONE_VOLUME = 100
        const val SHORT_MILLIS = 120
        const val LONG_MILLIS = 300
    }
}
