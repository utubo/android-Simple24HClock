package utb.dip.jp.simple24hclock

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Color.argb

data class AppWidgetProps(
    val id: Int,
    val minute: Float,
    val dayOfYear: Float,
    val dayOfYearDots: Float,
    var text: String? = "",
    var format: String? = "",
    var tapBehavior: String? = "",
    val backgroundAlpha: Float = 0.3F,
    val timezone: String? = "",
    var lat: Float = 0F,
    var lng: Float = 0F,
    var rotate: Float = 0F,
    var moonPhase: Boolean = false,
    var colorHour: Int = Color.WHITE,
    var colorMinute: Int = Color.WHITE,
    var colorDayOfYear: Int = Color.WHITE,
    var colorBorder: Int = Color.WHITE,
    var colorDots: Int = Color.WHITE,
    var colorDayOfYearDots: Int = Color.WHITE,
    var colorSun: Int = Color.WHITE,
    var colorMoon: Int = Color.WHITE,
    var colorDayArea: Int = Color.GRAY,
    var colorNightArea: Int = Color.BLACK,
    var colorText: Int = Color.WHITE,
    var updateNow: Boolean = false,
)

internal fun getAppWidgetProps(prefs: SharedPreferences, id: Int): AppWidgetProps {
    return AppWidgetProps(
        id = id,
        minute = prefs.getFloat("minute_$id", 0F),
        dayOfYear = prefs.getFloat("day_of_year_$id", 0F),
        dayOfYearDots = prefs.getFloat("day_of_year_dots_$id", 0F),
        text = prefs.getString("text_$id", ""),
        format = prefs.getString("format_$id", ""),
        tapBehavior = prefs.getString("tap_behavior_$id", ""),
        backgroundAlpha = prefs.getFloat("backgroundAlpha_$id", 0.4F),
        timezone = prefs.getString("timezone_$id", ""),
        lat = prefs.getFloat("lat_$id", 0F),
        lng = prefs.getFloat("lng_$id", 0F),
        rotate = prefs.getFloat("rotate_$id", 0F),
        moonPhase = prefs.getBoolean("moon_phase_$id", false),
        colorHour = prefs.getInt("color_hour_$id", Color.WHITE),
        colorMinute = prefs.getInt("color_minute_$id", argb(180, 255, 255, 255)),
        colorDayOfYear = prefs.getInt("color_day_of_year_$id", argb(160, 255, 255, 255)),
        colorBorder = prefs.getInt("color_border_$id", Color.WHITE),
        colorDots = prefs.getInt("color_dots_$id", Color.WHITE),
        colorDayOfYearDots = prefs.getInt("color_day_of_year_dots_$id", argb(160, 255, 255, 255)),
        colorSun = prefs.getInt("color_sun_$id", Color.WHITE),
        colorMoon = prefs.getInt("color_moon_$id", Color.WHITE),
        colorDayArea = prefs.getInt("color_day_area_$id", Color.GRAY),
        colorNightArea = prefs.getInt("color_night_area_$id", Color.BLACK),
        colorText = prefs.getInt("color_text_$id", argb(180, 255, 255, 255)),
        updateNow = prefs.getBoolean("update_now_$id", false)
    )
}

internal fun putAppWidgetProps(editor: SharedPreferences.Editor, props: AppWidgetProps) {
    val id = props.id
    editor.putInt("id_$id", props.id)
    editor.putFloat("minute_$id", props.minute)
    editor.putFloat("day_of_year_$id", props.dayOfYear)
    editor.putFloat("day_of_year_dots_$id", props.dayOfYearDots)
    editor.putString("text_$id", props.text)
    editor.putString("format_$id", props.format)
    editor.putString("tap_behavior_$id", props.tapBehavior)
    editor.putFloat("backgroundAlpha_$id", props.backgroundAlpha)
    editor.putString("timezone_$id", props.timezone)
    editor.putFloat("lat_$id", props.lat)
    editor.putFloat("lng_$id", props.lng)
    editor.putFloat("rotate_$id", props.rotate)
    editor.putBoolean("moon_phase_$id", props.moonPhase)
    editor.putInt("color_hour_$id", props.colorHour)
    editor.putInt("color_minute_$id", props.colorMinute)
    editor.putInt("color_day_of_year_$id", props.colorDayOfYear)
    editor.putInt("color_border_$id", props.colorBorder)
    editor.putInt("color_dots_$id", props.colorDots)
    editor.putInt("color_day_of_year_dots_$id", props.colorDayOfYearDots)
    editor.putInt("color_sun_$id", props.colorSun)
    editor.putInt("color_moon_$id", props.colorMoon)
    editor.putInt("color_day_area_$id", props.colorDayArea)
    editor.putInt("color_night_area_$id", props.colorNightArea)
    editor.putInt("color_text_$id", props.colorText)
    editor.putBoolean("update_now_$id", props.updateNow)
}