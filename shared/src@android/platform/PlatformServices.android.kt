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
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.room.Room
import androidx.room.RoomDatabase
import co.touchlab.kermit.Logger
import dev.justinsmith.interval.data.AppDatabase
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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

actual val materialBackPreview: Boolean = true

actual fun epochMillisNow(): Long = System.currentTimeMillis()

private class AndroidStorage(private val context: Context) : PlatformStorage {
    override fun databaseBuilder(): RoomDatabase.Builder<AppDatabase> =
        Room.databaseBuilder<AppDatabase>(context, context.getDatabasePath(DATABASE_NAME).absolutePath)

    override val preferencesPath: String =
        context.filesDir.resolve("datastore/$PREFERENCES_NAME").absolutePath
}

private class JavaTimeFormatter : TimestampFormatter {
    // Skeletons rather than fixed patterns, so each locale orders the parts and picks 12 or 24 hours its way.
    private val dateFormat = localized("EEEEMMMd")
    private val timeFormat = localized("jmm")

    override fun date(epochMillis: Long): String = dateFormat.format(zoned(epochMillis))

    override fun time(epochMillis: Long): String = timeFormat.format(zoned(epochMillis))

    override fun dayNumber(epochMillis: Long): Long = zoned(epochMillis).toLocalDate().toEpochDay()

    override fun weekday(epochMillis: Long): Int = zoned(epochMillis).dayOfWeek.value

    private fun zoned(epochMillis: Long) = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())

    private fun localized(skeleton: String): DateTimeFormatter {
        val locale = Locale.getDefault()
        return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
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
