package dev.justinsmith.interval.platform

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import co.touchlab.kermit.Logger
import dev.justinsmith.interval.data.KeyValueStore
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
actual fun rememberPlatformModule(): Module {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        module {
            single<KeyValueStore> { SharedPreferencesStore(context) }
            // Eager, so the speech engine has finished starting before the first workout cue.
            single<CuePlayer>(createdAtStart = true) { AndroidCuePlayer(context) }.withOptions {
                onClose { it?.release() }
            }
            single<TimestampFormatter> { JavaTimeFormatter() }
        }
    }
}

@Composable
actual fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

actual fun epochMillisNow(): Long = System.currentTimeMillis()

private class SharedPreferencesStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("interval", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}

private class JavaTimeFormatter : TimestampFormatter {
    private val formatter = DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a")

    override fun format(epochMillis: Long): String =
        formatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
}

private class AndroidCuePlayer(context: Context) : CuePlayer {
    private val log = Logger.withTag("AndroidCuePlayer")

    // STREAM_MUSIC so beeps follow the media volume and mix over the user's workout music.
    private val tones: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }
        .onFailure { log.w(it) { "No tone generator; beeps disabled" } }
        .getOrNull()

    // Set from the TextToSpeech init callback, which may run on another thread. A line asked for
    // before the engine is ready is held and spoken once it is, so the first cue isn't lost.
    @Volatile
    private var speechReady = false

    @Volatile
    private var pendingSpeech: String? = null

    private val speech: TextToSpeech = TextToSpeech(context) { status ->
        speechReady = status == TextToSpeech.SUCCESS
        pendingSpeech?.let { if (speechReady) say(it) }
        pendingSpeech = null
    }

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    override fun beep(beep: Beep) {
        val (tone, millis) = when (beep) {
            Beep.Countdown -> ToneGenerator.TONE_PROP_BEEP to 120
            Beep.Halfway -> ToneGenerator.TONE_PROP_ACK to 150
            Beep.Start -> ToneGenerator.TONE_PROP_BEEP2 to 200
            Beep.Work -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD to 300
            Beep.Rest -> ToneGenerator.TONE_PROP_BEEP2 to 300
            Beep.Finished -> ToneGenerator.TONE_CDMA_CONFIRM to 600
        }
        runCatching { tones?.startTone(tone, millis) }.onFailure { log.w(it) { "Beep failed" } }
    }

    override fun speak(text: String) {
        if (speechReady) say(text) else pendingSpeech = text
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
        runCatching { tones?.release() }
        runCatching { speech.shutdown() }
    }
}
