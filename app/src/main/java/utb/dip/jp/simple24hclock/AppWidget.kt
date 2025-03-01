package utb.dip.jp.simple24hclock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.lang.Float.min
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Implementation of App Widget functionality.
 */
class AppWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = context.getSharedPreferences(WIDGET_IDS_KEY, Context.MODE_PRIVATE)
        prefs.edit().apply {
            this.clear()
            for (appWidgetId in appWidgetIds) {
                putInt("widget_$appWidgetId", appWidgetId)
            }
            apply()
        }
        val layoutPrefs = context.getSharedPreferences(WIDGET_LAYOUT_KEY, Context.MODE_PRIVATE)
        layoutPrefs.edit().apply {
            this.clear()
            for (appWidgetId in appWidgetIds) {
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                calculateLayout(this, appWidgetId, options)
            }
            apply()
        }
        updateAllAppWidget(context, appWidgetManager)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        id: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, id, newOptions)
        val layoutPrefs = context.getSharedPreferences(WIDGET_LAYOUT_KEY, Context.MODE_PRIVATE)
        layoutPrefs.edit().apply {
            calculateLayout(this, id, newOptions)
            apply()
        }
        updateAppWidget(context, appWidgetManager, id)
    }
}

class WidgetUpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        updateAllAppWidget(context, appWidgetManager)
        scheduleNextWork(context)
        return Result.success()
    }

    private fun scheduleNextWork(context: Context) {
        val interval = 60000 - (System.currentTimeMillis() % 60000)
        val nextWorkRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setInitialDelay(interval, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueue(nextWorkRequest)
    }
}

internal fun calculateLayout(
    editor: SharedPreferences.Editor,
    id: Int,
    options: Bundle?
) {
    @Suppress("DEPRECATION") val sizes = options?.getParcelableArrayList<SizeF>(
        AppWidgetManager.OPTION_APPWIDGET_SIZES
    )
    if (sizes.isNullOrEmpty()) return
    val size = sizes[0]
    val s = min(size.width, size.height)
    editor.putFloat("text_size_$id", s / 14F)
}

internal fun updateAllAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager
) {
    val prefs = context.getSharedPreferences(WIDGET_IDS_KEY, Context.MODE_PRIVATE)
    prefs.all.forEach { (_, id) ->
        updateAppWidget(context, appWidgetManager, id as Int)
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val widgetPrefs = context.getSharedPreferences(WIDGET_PREF_KEY, Context.MODE_PRIVATE)

    // Hand
    val now = Calendar.getInstance()
    val r = 360F / 24F * (now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60F)
    val views = RemoteViews(context.packageName, R.layout.app_widget)
    views.setFloat(R.id.HandImageView, "setRotation", r)

    // Day of year
    val dayOfYear = widgetPrefs.getFloat("day_of_year_$appWidgetId", 0F)
    views.setFloat(R.id.DayOfYearHandImageView, "setAlpha", dayOfYear)
    if (0 < dayOfYear) {
        val maxY = now.getMaximum(Calendar.DAY_OF_YEAR)
        val y = 360F / maxY * now.get(Calendar.DAY_OF_YEAR)
        views.setFloat(R.id.DayOfYearHandImageView, "setRotation", y)
    }

    // Text
    val text = widgetPrefs.getString("text_$appWidgetId", "")
    if (text.isNullOrEmpty()) {
        views.setTextViewText(R.id.textView, "")
    } else {
        val layoutPrefs = context.getSharedPreferences(WIDGET_LAYOUT_KEY, Context.MODE_PRIVATE)
        val textSize = layoutPrefs.getFloat("text_size_$appWidgetId", 0F)
        if (textSize != 0F) {
            views.setFloat(R.id.textView, "setTextSize", textSize)
            val fmt = SimpleDateFormat(text, java.util.Locale.getDefault())
            views.setTextViewText(R.id.textView, fmt.format(now.time))
        }
    }
    appWidgetManager.updateAppWidget(appWidgetId, views)
}