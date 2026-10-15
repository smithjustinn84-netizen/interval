package dev.justinsmith.interval.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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

@Composable
actual fun SystemBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !lightIcons
            isAppearanceLightNavigationBars = !lightIcons
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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

/** A [ToneGenerator] tone type and how long to play it. */
private class Tone(val type: Int, val millis: Int)
