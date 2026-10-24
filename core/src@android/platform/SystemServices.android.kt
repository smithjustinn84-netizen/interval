package dev.justinsmith.interval.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import android.text.format.DateFormat
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.WeakHashMap

// `keepScreenOn` is one flag per view, so one count per view; weak, so a closed Activity's view can go.
private val awakeHolds = WeakHashMap<View, AwakeHolds>()

@Composable
actual fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        val holds = awakeHolds.getOrPut(view, ::AwakeHolds)
        if (holds.acquire()) view.keepScreenOn = true
        onDispose { if (holds.release()) view.keepScreenOn = false }
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

/** "Remove animations" in Android's accessibility settings sets the animator duration scale to 0. */
@Composable
actual fun prefersReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

actual val materialBackPreview: Boolean = true

actual val platformPageTransitions: Boolean = false

actual fun epochMillisNow(): Long = System.currentTimeMillis()

actual fun systemTimestampFormatter(): TimestampFormatter = JavaTimeFormatter()

private class JavaTimeFormatter : TimestampFormatter {
    // Built again when the locale changes: the container outlives the Activity a language change recreates.
    private var formats: Formats? = null

    override fun date(epochMillis: Long): String = formats().date.format(zoned(epochMillis))

    override fun time(epochMillis: Long): String = formats().time.format(zoned(epochMillis))

    override fun dayNumber(epochMillis: Long): Long = zoned(epochMillis).toLocalDate().toEpochDay()

    override fun dateOfDay(epochDay: Long): String = formats().date.format(LocalDate.ofEpochDay(epochDay))

    override fun monthTitle(epochDay: Long): String = formats().month.format(LocalDate.ofEpochDay(epochDay))

    private fun zoned(epochMillis: Long) = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())

    private fun formats(): Formats {
        val locale = Locale.getDefault()
        return formats?.takeIf { it.locale == locale } ?: Formats(locale).also { formats = it }
    }

    // Skeletons rather than fixed patterns, so each locale orders the parts and picks 12 or 24 hours its way.
    private class Formats(val locale: Locale) {
        val date: DateTimeFormatter = localized("EEEEMMMd")
        val time: DateTimeFormatter = localized("jmm")
        val month: DateTimeFormatter = localized("yMMMM")

        private fun localized(skeleton: String): DateTimeFormatter =
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
}
