package utb.dip.jp.simple24hclock
import android.content.SharedPreferences

data class AppWidgetProps(
    val minute: Float,
    val dayOfYear: Float,
    val dayOfYearDots: Float,
    val text: String? = "",
    val format: String? = "",
    val tapBehavior: String? = "",
)

internal fun getAppWidgetProps(prefs: SharedPreferences, id: Int): AppWidgetProps {
    return AppWidgetProps(
        minute = prefs.getFloat("minute_$id", 0F),
        dayOfYear = prefs.getFloat("day_of_year_$id", 0F),
        dayOfYearDots = prefs.getFloat("day_of_year_dots_$id", 0F),
        text = prefs.getString("text_$id", ""),
        format = prefs.getString("format_$id", ""),
        tapBehavior = prefs.getString("tap_behavior_$id", ""),
    )
}

internal fun putAppWidgetProps(editor: SharedPreferences.Editor, id: Int, props: AppWidgetProps) {
    editor.putFloat("minute_$id", props.minute)
    editor.putFloat("day_of_year_$id", props.dayOfYear)
    editor.putFloat("day_of_year_dots_$id", props.dayOfYearDots)
    editor.putString("text_$id", props.text)
    editor.putString("format_$id", props.format)
    editor.putString("tap_behavior_$id", props.tapBehavior)
}