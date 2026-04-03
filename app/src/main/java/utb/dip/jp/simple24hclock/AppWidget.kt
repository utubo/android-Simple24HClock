package utb.dip.jp.simple24hclock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.AlarmClock.ACTION_SHOW_ALARMS
import android.util.SizeF
import android.widget.RemoteViews
import androidx.core.net.toUri
import java.lang.Float.min
import java.text.SimpleDateFormat
import java.util.Calendar

private const val MASK_OPAQUE = 0xFF000000.toInt()

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
    val props = getAppWidgetProps(widgetPrefs, appWidgetId)
    updateAppWidgetContent(context, views, props)

    // Tap
    val intent = when (props.tapBehavior) {
        "alarm" -> Intent(ACTION_SHOW_ALARMS)
        "calendar" -> Intent(ACTION_VIEW).apply {
            data = "content://com.android.calendar/time".toUri()
        }

        else -> Intent()
    }
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.Container, pendingIntent)
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

internal fun updateAppWidgetContent(context: Context, views: RemoteViews, props: AppWidgetProps) {
    val now = Calendar.getInstance()

    // Hour
    val h = 360F / 24F * (now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60F)
    views.setFloat(R.id.HourHandImageView, "setRotation", h)

    // Minute
    if (0 < props.minute) {
        val m = 360F / 60F * now.get(Calendar.MINUTE)
        views.setFloat(R.id.MinuteHandImageView, "setRotation", m)
    }

    // Day of year
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

    // Back
    val metrics = context.resources.displayMetrics
    val maxSize = minOf(metrics.widthPixels, metrics.heightPixels)
    val bgBitmap =
        SunCycleManager.getOrUpdateBackground(context, props, maxSize) // size in pixels
    views.setImageViewBitmap(R.id.BackImageView, bgBitmap)

    // Text
    if (props.text.isNullOrEmpty()) {
        views.setTextViewText(R.id.textView, "")
    } else {
        val fmt = SimpleDateFormat(props.text, java.util.Locale.getDefault())
        views.setTextViewText(R.id.textView, fmt.format(now.time))
    }

    // Rotation
    views.setFloat(R.id.RotateBg, "setRotation", props.rotate)
    views.setFloat(R.id.RotateFg, "setRotation", props.rotate)

    // Colors
    fun setColor(id: Int, color: Int, visible: Float = 1F) {
        if (visible != 0F) {
            views.setInt(id, "setColorFilter", color or MASK_OPAQUE)
            views.setFloat(id, "setAlpha", ((color shr 24) and 0xFF) / 255F)
        } else {
            views.setFloat(id, "setAlpha", 0F)
        }
    }
    setColor(R.id.HourHandImageView, props.colorHour)
    setColor(R.id.MinuteHandImageView, props.colorMinute, props.minute)
    setColor(R.id.DayOfYearHandImageView, props.colorDayOfYear, props.dayOfYear)
    setColor(R.id.FaceImageView, props.colorFace)
    setColor(R.id.DayOfYearDotsImageView, props.colorDayOfYearDots, props.dayOfYearDots)
    setColor(R.id.SunImageView, props.colorSun)
    setColor(R.id.MoonImageView, props.colorMoon)
    views.setInt(R.id.textView, "setTextColor", props.colorText or MASK_OPAQUE)
    views.setFloat(R.id.textView, "setAlpha", ((props.colorText shr 24) and 0xFF) / 255F)
    views.setFloat(R.id.BackImageView, "setAlpha", props.backgroundAlpha)

}
