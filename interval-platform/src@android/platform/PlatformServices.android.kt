package dev.justinsmith.interval.platform

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.room.Room
import androidx.room.RoomDatabase
import co.touchlab.kermit.Logger
import dev.justinsmith.interval.data.AppDatabase
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

@Composable
actual fun rememberPlatformModule(): Module {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        module {
            single<PlatformStorage> { AndroidStorage(context) }
            // Eager, so the speech engine has finished starting before the first workout cue.
            single<CuePlayer>(createdAtStart = true) { AndroidCuePlayer(context) }.withOptions {
                onClose { it?.release() }
            }
            single<TimestampFormatter> { systemTimestampFormatter() }
            single<AppInfo> { AndroidAppInfo(context) }
            single<BackgroundWorkout> { AndroidBackgroundWorkout(context) }
            single<PlanReminders> { AndroidPlanReminders(context) }
        }
    }
}

private class AndroidStorage(private val context: Context) : PlatformStorage {
    override fun databaseBuilder(): RoomDatabase.Builder<AppDatabase> =
        Room.databaseBuilder<AppDatabase>(context, context.getDatabasePath(DATABASE_NAME).absolutePath)

    override val preferencesPath: String =
        context.filesDir.resolve("datastore/$PREFERENCES_NAME").absolutePath
}

/** The `versionName` of the installed package: `versionName` in `androidApp/module.yaml`. */
private class AndroidAppInfo(context: Context) : AppInfo {
    override val version: String? = runCatching {
        val manager = context.packageManager
        val name = context.packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPackageInfo(name, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            manager.getPackageInfo(name, 0)
        }.versionName
    }.getOrNull()
}

private class AndroidCuePlayer(context: Context) : CuePlayer {
    private val log = Logger.withTag("AndroidCuePlayer")

    // STREAM_MUSIC so beeps follow the media volume and mix over the user's workout music.
    private val tones: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }
        .onFailure { log.w(it) { "No tone generator; beeps disabled" } }
        .getOrNull()

    // A line asked for before the engine is ready is held and spoken once it is, so the first cue isn't
    // lost. Both fields are touched only on the main thread: SpeechQueue hands lines over there, and the
    // init callback, which may run on another thread, is posted there. Checking and holding a line on
    // two threads at once could drop it between the check and the hold.
    private val main = Handler(Looper.getMainLooper())
    private var speechReady = false
    private var pendingSpeech: String? = null

    private val speech: TextToSpeech = TextToSpeech(context) { status ->
        main.post {
            speechReady = status == TextToSpeech.SUCCESS
            pendingSpeech?.let { if (speechReady) say(it) }
            pendingSpeech = null
        }
    }

    private val lines = SpeechQueue { text -> if (speechReady) say(text) else pendingSpeech = text }

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

    private fun say(text: String) {
        runCatching { speech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cue") }
            .onFailure { log.w(it) { "Speech failed" } }
    }

    override fun vibrate(haptic: Haptic) {
        val timings = when (haptic) {
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
        runCatching { speech.shutdown() }
    }
}

/** A [ToneGenerator] tone type and how long to play it. */
private class Tone(val type: Int, val millis: Int)
