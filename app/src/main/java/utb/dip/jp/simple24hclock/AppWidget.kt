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

class ScreenOnReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_ON) {
            restart(context)
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
    updateAppWidgetContent(views, AppWidgetContentProps(
        widgetPrefs.getFloat("day_of_year_$appWidgetId", 0F),
        widgetPrefs.getFloat("day_of_year_dots_$appWidgetId", 0F),
        widgetPrefs.getString("text_$appWidgetId", "")
    ))
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

internal data class AppWidgetContentProps(
    val dayOfYear: Float,
    val dayOfYearDots: Float,
    val text: String?,
)

internal fun updateAppWidgetContent(views: RemoteViews, props: AppWidgetContentProps) {
    // Hand
    val now = Calendar.getInstance()
    val h = 360F / 24F * (now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60F)
    views.setFloat(R.id.HandImageView, "setRotation", h)

    // Day of year
    views.setFloat(R.id.DayOfYearDotsImageView, "setAlpha", props.dayOfYearDots)
    views.setFloat(R.id.DayOfYearHandImageView, "setAlpha", props.dayOfYear)
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