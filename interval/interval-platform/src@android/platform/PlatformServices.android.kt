package dev.justinsmith.interval.platform

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
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

private class AndroidCuePlayer(context: Context, speaker: Speaker) : CuePlayer {
    private val log = Logger.withTag("AndroidCuePlayer")

    // STREAM_MUSIC so beeps follow the media volume and mix over the user's workout music.
    private val tones: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }
        .onFailure { log.w(it) { "No tone generator; beeps disabled" } }
        .getOrNull()

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
        val tone = when (beep) {
            Beep.Countdown -> Tone(ToneGenerator.TONE_PROP_BEEP, millis = 120)
            Beep.Halfway -> Tone(ToneGenerator.TONE_PROP_ACK, millis = 150)
            Beep.Start -> Tone(ToneGenerator.TONE_PROP_BEEP2, millis = 200)
            Beep.Work -> Tone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, millis = 300)
            Beep.Rest -> Tone(ToneGenerator.TONE_PROP_BEEP2, millis = 300)
            Beep.Finished -> Tone(ToneGenerator.TONE_CDMA_CONFIRM, millis = 600)
        }
        runCatching { tones?.startTone(tone.type, tone.millis) }.onFailure { log.w(it) { "Beep failed" } }
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
        runCatching { tones?.release() }
    }
}

/** A [ToneGenerator] tone type and how long to play it. */
private class Tone(val type: Int, val millis: Int)
