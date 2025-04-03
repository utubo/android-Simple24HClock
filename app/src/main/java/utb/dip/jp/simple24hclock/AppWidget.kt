package utb.dip.jp.simple24hclock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
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
        restart(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        restart(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        stop(context)
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        super.onDeleted(context, appWidgetIds)
        cleanupPrefs(context, appWidgetIds)
    }

    override fun onRestored(context: Context?, oldWidgetIds: IntArray?, newWidgetIds: IntArray?) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        if (context == null) return
        val widgetPrefs = context.getSharedPreferences(WIDGET_PREF_KEY, Context.MODE_PRIVATE)
        val oldPrefs = widgetPrefs.all.toMap()
        val newWidgetIdsSize = newWidgetIds?.size ?: 0
        widgetPrefs.edit().apply {
            oldPrefs.forEach { (key, value) ->
                val id = key.split("_").last().toIntOrNull() ?: -1
                val i = oldWidgetIds?.indexOf(id) ?: -1
                if (i in 0..<newWidgetIdsSize) {
                    remove(key)
                    val newId = newWidgetIds?.get(i)
                    val newKey = key.replace("_$id", "_$newId")
                    when (value) {
                        is Int -> putInt(newKey, value)
                        is Float -> putFloat(newKey, value)
                        is String -> putString(newKey, value)
                        is Boolean -> putBoolean(newKey, value)
                    }
                }
            }
            apply()
        }
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

class MyBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_MY_PACKAGE_UNSUSPENDED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> restart(context)
        }
    }
}

class WidgetUpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    companion object {
        private const val WORK_NAME = "Simple24HClockUpdateWorker"
        fun enqueue(context: Context, delay: Long = 0) {
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            if (delay == 0L) {
                request.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            } else {
                request.setInitialDelay(delay, TimeUnit.MILLISECONDS)
            }
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request.build())
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        // update widgets
        val appWidgetManager = AppWidgetManager.getInstance(context)
        updateAllAppWidgets(context, appWidgetManager)
        // schedule next
        val delay = 60000 - (System.currentTimeMillis() % 60000)
        enqueue(context, delay)
        return Result.success()
    }
}

internal fun restart(context: Context) {
    WidgetUpdateWorker.enqueue(context)
}

internal fun stop(context: Context) {
    WidgetUpdateWorker.cancel(context)
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
    editor.putFloat("text_size_$id", min(size.width, size.height) / 14)
}

internal fun updateAllAppWidgets(
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
    val views = RemoteViews(context.packageName, R.layout.app_widget)
    val layoutPrefs = context.getSharedPreferences(WIDGET_LAYOUT_KEY, Context.MODE_PRIVATE)
    val textSize = layoutPrefs.getFloat("text_size_$appWidgetId", 0F)
    if (textSize != 0F) {
        views.setFloat(R.id.textView, "setTextSize", textSize)
    }
    val widgetPrefs = context.getSharedPreferences(WIDGET_PREF_KEY, Context.MODE_PRIVATE)
    updateAppWidgetContent(
        views, AppWidgetContentProps(
            widgetPrefs.getFloat("minute_$appWidgetId", 0F),
            widgetPrefs.getFloat("day_of_year_$appWidgetId", 0F),
            widgetPrefs.getFloat("day_of_year_dots_$appWidgetId", 0F),
            widgetPrefs.getString("text_$appWidgetId", "")
        )
    )
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

internal data class AppWidgetContentProps(
    val minute: Float,
    val dayOfYear: Float,
    val dayOfYearDots: Float,
    val text: String?,
)

internal fun updateAppWidgetContent(views: RemoteViews, props: AppWidgetContentProps) {
    val now = Calendar.getInstance()

    // Hour
    val h = 360F / 24F * (now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60F)
    views.setFloat(R.id.HandImageView, "setRotation", h)

    // Minute
    views.setFloat(R.id.MinuteHandImageView, "setAlpha", props.minute)
    if (0 < props.minute) {
        val m = 360F / 60F * now.get(Calendar.MINUTE)
        views.setFloat(R.id.MinuteHandImageView, "setRotation", m)
    }

    // Day of year
    views.setFloat(R.id.DayOfYearHandImageView, "setAlpha", props.dayOfYear)
    views.setFloat(R.id.DayOfYearDotsImageView, "setAlpha", props.dayOfYearDots)
    if (0 < props.dayOfYear) {
        if (0 < props.dayOfYearDots) {
            val m = now.get(Calendar.MONDAY).toFloat()
            val dOfM =
                (now.get(Calendar.DAY_OF_MONTH) - 1).toFloat() / now.getLeastMaximum(Calendar.DAY_OF_MONTH)
            val d = 360F / 12 * (m + dOfM)
            views.setFloat(R.id.DayOfYearHandImageView, "setRotation", d)
        } else {
            val maxDoY = now.getMaximum(Calendar.DAY_OF_YEAR)
            val d = 360F / maxDoY * (now.get(Calendar.DAY_OF_YEAR) - 1)
            views.setFloat(R.id.DayOfYearHandImageView, "setRotation", d)
        }
    }

    // Text
    if (props.text.isNullOrEmpty()) {
        views.setTextViewText(R.id.textView, "")
    } else {
        val fmt = SimpleDateFormat(props.text, java.util.Locale.getDefault())
        views.setTextViewText(R.id.textView, fmt.format(now.time))
    }
}

internal fun cleanupPrefs(context: Context?, deletedIds: IntArray?) {
    if (context == null) return
    val idsPref = context.getSharedPreferences(WIDGET_IDS_KEY, Context.MODE_PRIVATE)
    if (deletedIds != null) {
        idsPref.edit().apply {
            deletedIds.forEach { id ->
                remove("widget_$id")
            }
            apply()
        }
    }
    val ids = mutableSetOf<Int>()
    idsPref.all.forEach { (_, id) ->
        ids.add(id as Int)
    }
    val prefs = context.getSharedPreferences(WIDGET_PREF_KEY, Context.MODE_PRIVATE)
    prefs.edit().apply {
        prefs.all.forEach { (key, _) ->
            val id = key.split('_').last().toIntOrNull()
            if (id != null && !ids.contains(id)) {
                remove(key)
            }
        }
        apply()
    }
}
