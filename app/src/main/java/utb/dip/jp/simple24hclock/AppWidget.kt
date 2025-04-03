package utb.dip.jp.simple24hclock

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import java.lang.Float.min
import java.text.SimpleDateFormat
import java.util.Calendar

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
    updateAppWidgetContent(views, getAppWidgetProps(widgetPrefs, appWidgetId))
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

internal fun updateAppWidgetContent(views: RemoteViews, props: AppWidgetProps) {
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
