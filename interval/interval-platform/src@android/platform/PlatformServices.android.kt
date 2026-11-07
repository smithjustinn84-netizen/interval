package dev.justinsmith.interval.platform

import android.content.Context
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
 * asks for it as its UI opens. Its voice is core's [Speaker] and its beeps core's [TonePlayer], which Lift's
 * timers speak and beep through too.
 */
fun platformModule(context: Context): Module = context.applicationContext.let { app ->
    module {
        single<PlatformStorage> { AndroidStorage(app) }
        single<CuePlayer> { AndroidCuePlayer(app, get(), get()) }.withOptions {
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

/** Core's [TonePlayer] for the beeps, core's [Speaker] for the voice and the vibrator for the haptics. */
private class AndroidCuePlayer(context: Context, speaker: Speaker, private val tones: TonePlayer) : CuePlayer {
    private val log = Logger.withTag("AndroidCuePlayer")

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
        tones.play(beep.tone)
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

    // The tones are the process's, core's, and outlive this player as the voice does.
    override fun release() {
        lines.close()
    }
}
