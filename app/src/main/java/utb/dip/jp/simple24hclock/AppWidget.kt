package utb.dip.jp.simple24hclock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.AlarmClock.ACTION_SHOW_ALARMS
import android.view.View
import android.widget.RemoteViews
import androidx.core.net.toUri
import java.text.SimpleDateFormat
import java.util.Calendar
import kotlin.math.min

private const val MASK_OPAQUE = 0xFF000000.toInt()

internal fun calculateLayout(
    context: Context,
    editor: SharedPreferences.Editor,
    id: Int,
    bundle: Bundle
) {
    val size = min(
        bundle.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
        bundle.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
    )
    editor.putFloat("text_size_$id", size.toFloat() / 14)
    editor.putFloat("size_$id", size.toFloat() * context.resources.displayMetrics.density)
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val views = RemoteViews(context.packageName, R.layout.app_widget)
    val layoutPrefs = context.getSharedPreferences(WIDGET_LAYOUT_KEY, Context.MODE_PRIVATE)
    val size = layoutPrefs.getFloat("size_$appWidgetId", 0F)
    val textSize = layoutPrefs.getFloat("text_size_$appWidgetId", 0F)
    if (textSize != 0F) {
        views.setFloat(R.id.tv_label, "setTextSize", textSize)
    }
    val widgetPrefs = context.getSharedPreferences(WIDGET_PREF_KEY, Context.MODE_PRIVATE)
    val props = getAppWidgetProps(widgetPrefs, appWidgetId)
    updateAppWidgetContent(context, views, props, size)

    // Tap
    val intent = when (props.tapBehavior ?: "") {
        "" -> Intent()
        "alarm" -> Intent(ACTION_SHOW_ALARMS)
        "calendar" -> Intent(ACTION_VIEW).apply {
            data = "content://com.android.calendar/time".toUri()
        }

        else -> {
            val comp = ComponentName.unflattenFromString(props.tapBehavior!!)
            if (comp?.packageName == context.packageName)
                Intent(context, SettingsActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
            else
                Intent().apply { component = comp }
        }
    }
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.container, pendingIntent)
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

internal fun updateAppWidgetContent(
    context: Context,
    views: RemoteViews,
    props: AppWidgetProps,
    size: Float
) {
    val now = Calendar.getInstance()

    // Hour
    val hour = now.get(Calendar.HOUR_OF_DAY)
    val hourDeg = 360F / 24F * (hour + now.get(Calendar.MINUTE) / 60F)
    views.setFloat(R.id.iv_hour, "setRotation", hourDeg)

    // Minute
    if (0 < props.minute) {
        val m = 360F / 60F * now.get(Calendar.MINUTE)
        views.setFloat(R.id.iv_minute, "setRotation", m)
    }

    // Day of year
    if (0 < props.dayOfYear) {
        if (0 < props.dayOfYearDots) {
            val m = now.get(Calendar.MONDAY).toFloat()
            val dOfM =
                (now.get(Calendar.DAY_OF_MONTH) - 1).toFloat() / now.getLeastMaximum(Calendar.DAY_OF_MONTH)
            val d = 360F / 12 * (m + dOfM)
            views.setFloat(R.id.iv_day_of_year, "setRotation", d)
        } else {
            val maxDoY = now.getMaximum(Calendar.DAY_OF_YEAR)
            val d = 360F / maxDoY * (now.get(Calendar.DAY_OF_YEAR) - 1)
            views.setFloat(R.id.iv_day_of_year, "setRotation", d)
        }
    }

    // Back
    val bgBitmap =
        SunCycleManager.getOrUpdateBackground(
            context,
            props,
            size.toInt()
        )
    views.setImageViewBitmap(R.id.iv_background, bgBitmap)

    // Text
    if (props.text.isNullOrEmpty()) {
        views.setTextViewText(R.id.tv_label, "")
    } else {
        val fmt = SimpleDateFormat(props.text, java.util.Locale.getDefault())
        views.setTextViewText(R.id.tv_label, fmt.format(now.time))
    }

    // Moon phase
    val coordinates = if (props.moonPhase) LatLng.getCoordinates(context, props) else null
    var moonRotate = 1F
    if (coordinates != null) {
        views.setImageViewResource(R.id.iv_moon, MoonPhase.getMoonPhase())
        moonRotate = if (0 <= coordinates.first) 1F else -1F
    } else {
        views.setImageViewResource(R.id.iv_moon, R.drawable.moon_7)
    }

    // Sun and Moon position
    val isInnerSunAndMoon =
        props.rotate == ROTATE_FIX_HOUR_HAND && props.minuteAndHourDots != WP_HIDDEN
    var sunAndMoonPadding = 0F
    if (isInnerSunAndMoon) {
        sunAndMoonPadding = size * 0.12F
        views.setImageViewResource(R.id.iv_hour_dots, R.drawable.dots_hour_skiped)
        views.setImageViewResource(R.id.iv_month_dots, R.drawable.dots_month_skiped)
        views.setImageViewResource(R.id.iv_minute_dots, R.drawable.dots_minute_full)
    } else {
        views.setImageViewResource(R.id.iv_hour_dots, R.drawable.dots_hour_full)
        views.setImageViewResource(R.id.iv_month_dots, R.drawable.dots_month_full)
        views.setImageViewResource(R.id.iv_minute_dots, R.drawable.dots_minute_skiped)
    }
    views.setFloat(R.id.iv_sun, "setTranslationY", sunAndMoonPadding)
    views.setFloat(R.id.iv_moon, "setTranslationY", -sunAndMoonPadding)

    // Rotation
    val deg = when (props.rotate) {
        ROTATE_AUTO -> if (hour in 6..17) 0F else 180F
        ROTATE_FIX_HOUR_HAND -> -hourDeg - 180F
        else -> props.rotate
    }
    views.setFloat(R.id.rl_background, "setRotation", deg)
    views.setFloat(R.id.rl_foreground, "setRotation", deg)
    if (deg != 0F || props.rotate == ROTATE_FIX_HOUR_HAND) {
        moonRotate *= -1
    }
    views.setFloat(R.id.iv_moon, "setScaleX", moonRotate)

    // Colors
    fun setColor(id: Int, color: Int, visible: Float = 1F) {
        if (visible != 0F) {
            views.setInt(id, "setColorFilter", color or MASK_OPAQUE)
            views.setFloat(id, "setAlpha", ((color shr 24) and 0xFF) / 255F)
            views.setViewVisibility(id, View.VISIBLE)
        } else {
            views.setViewVisibility(id, View.INVISIBLE)
        }
    }

    // Hand
    setColor(R.id.iv_hour, props.colorHour)
    setColor(R.id.iv_minute, props.colorMinute, props.minute)
    setColor(R.id.iv_day_of_year, props.colorDayOfYear, props.dayOfYear)
    setColor(R.id.iv_border, props.colorBorder)

    // Dots
    if (props.minuteAndHourDots == 0F) {
        setColor(R.id.iv_dots, props.colorDots)
        setColor(R.id.iv_hour_dots, 0, 0F)
        setColor(R.id.iv_minute_dots, 0, 0F)
    } else {
        setColor(R.id.iv_dots, 0, 0F)
        setColor(R.id.iv_hour_dots, props.colorDots)
        setColor(R.id.iv_minute_dots, props.colorMinuteDots)
    }
    setColor(R.id.iv_month_dots, props.colorDayOfYearDots, props.dayOfYearDots)

    // Others
    setColor(R.id.iv_sun, props.colorSun)
    setColor(R.id.iv_moon, props.colorMoon)
    views.setFloat(R.id.tv_label, "setAlpha", ((props.colorText shr 24) and 0xFF) / 255F)
    views.setFloat(R.id.iv_background, "setAlpha", props.backgroundAlpha)
    views.setInt(R.id.tv_label, "setTextColor", props.colorText or MASK_OPAQUE)

}
