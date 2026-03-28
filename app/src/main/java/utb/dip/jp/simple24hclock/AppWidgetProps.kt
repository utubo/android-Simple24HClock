package utb.dip.jp.simple24hclock

import android.content.SharedPreferences

data class AppWidgetProps(
    val id: Int,
    val minute: Float,
    val dayOfYear: Float,
    val dayOfYearDots: Float,
    val text: String? = "",
    val format: String? = "",
    val tapBehavior: String? = "",
    val backgroundAlpha: Float = 0.3F,
    val timezone: String? = "",
    var lat: Float = 0F,
    var lng: Float = 0F,
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
        lng = prefs.getFloat("lat_$id", 0F),
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

}