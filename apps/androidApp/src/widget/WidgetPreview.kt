package dev.justinsmith.interval.widget

import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import co.touchlab.kermit.Logger

/**
 * Publishes the widget's preview for the launcher's picker: [WeekWidget.providePreview] drawn by Glance and
 * handed to the system, which keeps it until the next publish. Android 15 and up; earlier launchers show the
 * app's icon. The system allows a few publishes an hour, so it is done once per version of the app, from
 * `IntervalApplication` as the process starts, and tried again at the next start when the system refuses.
 */
internal object WidgetPreview {
    private const val PREFERENCES = "widget"
    private const val PUBLISHED_VERSION = "preview_version"
    private val log = Logger.withTag("WidgetPreview")

    suspend fun publish(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val version = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getLong(PUBLISHED_VERSION, -1) == version) return
        val result = GlanceAppWidgetManager(context).setWidgetPreviews(WeekWidgetReceiver::class)
        if (result == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) {
            preferences.edit().putLong(PUBLISHED_VERSION, version).apply()
        } else {
            log.w { "The system put off the widget's preview (result $result); next start" }
        }
    }
}
