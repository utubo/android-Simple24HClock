package utb.dip.jp.simple24hclock

import android.content.Context
import org.json.JSONArray
import java.util.TimeZone

object LatLng {

    private var cachedCoordinates: Pair<Double, Double>? = null

    internal fun getCoordinates(
        context: Context,
        props: AppWidgetProps,
    ): Pair<Double, Double>? {
        // Form cache
        cachedCoordinates?.let { return it }

        val zoneId = TimeZone.getDefault().id

        // From SharedPreference
        if (props.timezone == zoneId) {
            cachedCoordinates = Pair(props.lat.toDouble(), props.lng.toDouble())
            return cachedCoordinates
        }

        // From JSON
        return try {
            val jsonString =
                context.assets.open("timezones.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            var lat = 0.0
            var lng = 0.0
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.getString("id") == zoneId) {
                    lat = obj.getDouble("lat")
                    lng = obj.getDouble("lng")
                    break
                }
            }
            cachedCoordinates = Pair(lat, lng)
            props.lat = lat.toFloat()
            props.lng = lng.toFloat()
            props.updateNow = false
            val prefs = context.getSharedPreferences(WIDGET_PREF_KEY, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putAppWidgetProps(this, props)
                apply()
            }
            cachedCoordinates
        } catch (_: Exception) {
            null
        }
    }
}
