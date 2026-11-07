package dev.justinsmith.interval.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.room.Room
import androidx.room.RoomDatabase
import co.touchlab.kermit.Logger
import dev.justinsmith.interval.data.AppDatabase
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

/**
 * The interval side's services on Android, over the application's [context]. The [CuePlayer] is built on first
 * use rather than with the container, which also starts for a reminder's alarm with no screen to cue: the app
 * asks for it as its UI opens. Its voice is core's [Speaker], which Lift speaks through too.
 */
fun platformModule(context: Context): Module = context.applicationContext.let { app ->
    module {
        single<PlatformStorage> { AndroidStorage(app) }
        single<CuePlayer> { AndroidCuePlayer(app, get()) }.withOptions {
            onClose { it?.release() }
        }
        single<BackgroundWorkout> { AndroidBackgroundWorkout(app) }
        // One record of the alarms per process: DataStore's rule of one instance per file.
        single { PlanAlarms(app, planAlarmStore(app), get()) }
        single<PlanReminders> { AndroidPlanReminders(get()) }
    }
}

private class AndroidStorage(private val context: Context) : PlatformStorage {
    override fun databaseBuilder(): RoomDatabase.Builder<AppDatabase> =
        Room.databaseBuilder<AppDatabase>(context, context.getDatabasePath(DATABASE_NAME).absolutePath)

    override val preferencesPath: String =
        context.filesDir.resolve("datastore/$PREFERENCES_NAME").absolutePath
}

/**
 * [ToneSynth]'s tones through [AudioTrack]s on the media stream, core's [Speaker] for the voice and the vibrator for
 * the haptics.
 */
private class AndroidCuePlayer(context: Context, speaker: Speaker) : CuePlayer {
    private val log = Logger.withTag("AndroidCuePlayer")

    private val tones = ToneTracks(log)

    // The process's voice, core's: its engine starts with it, and outlives this player.
    private val lines = SpeechQueue(say = speaker::speak)

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    override fun beep(beep: Beep) {
        tones.play(beep)
    }

    override fun speak(speech: Speech) {
        lines.speak(speech)
    }

    override fun vibrate(haptic: Haptic) {
        val timings = when (haptic) {
            Haptic.Countdown -> longArrayOf(0, 30)
            Haptic.Work -> longArrayOf(0, 150)
            Haptic.Rest -> longArrayOf(0, 70, 60, 70)
            Haptic.Finished -> longArrayOf(0, 250, 100, 250)
        }
        // SecurityException without the VIBRATE permission; treated like any other missing cue.
        runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(timings, -1)) }
            .onFailure { log.w(it) { "Vibration failed" } }
    }

    override fun release() {
        lines.close()
        tones.release()
    }
}

/**
 * The six tones as static [AudioTrack]s, each written from [ToneSynth]'s samples the first time it plays and kept
 * for the next. On the media stream (`USAGE_MEDIA`), so they follow the media volume and mix over the user's
 * workout music, as sonification. A failure to build or play one is logged and the cue goes unheard.
 */
private class ToneTracks(private val log: Logger) {
    private val tracks = mutableMapOf<Beep, AudioTrack>()

    fun play(beep: Beep) {
        runCatching {
            val track = tracks.getOrPut(beep) { build(beep) }
            // A static track plays to its end and stays there: back to the start before it plays again.
            if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop()
            track.reloadStaticData()
            track.play()
        }.onFailure { log.w(it) { "Beep failed" } }
    }

    private fun build(beep: Beep): AudioTrack {
        val pcm = ToneSynth.pcm16(beep)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(ToneSynth.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * ToneSynth.BYTES_PER_SAMPLE)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
            .also { it.write(pcm, 0, pcm.size) }
    }

    fun release() {
        tracks.values.forEach { runCatching { it.release() } }
        tracks.clear()
    }
}
