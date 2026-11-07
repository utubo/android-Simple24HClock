package utb.dip.jp.simple24hclock

import android.content.Context
import org.json.JSONArray
import java.util.TimeZone

object LatLng {

    private var cachedCoordinates: Pair<Double, Double>? = null

    internal fun clearCache() {
        cachedCoordinates = null
    }

    internal fun getCoordinates(
        context: Context,
        props: AppWidgetProps,
    ): Pair<Double, Double>? {
        if (props.timezone == LOCATION_FIXED && !props.lat.isNaN() || !props.lng.isNaN()) {
            return Pair(props.lat.toDouble(), props.lng.toDouble())
        }

        // Form cache
        cachedCoordinates?.let { return it }

        val zoneId = TimeZone.getDefault().id

        // From SharedPreference
        if (props.timezone == zoneId) {
            cachedCoordinates = Pair(props.lat.toDouble(), props.lng.toDouble())
            return cachedCoordinates
        }

        // From TimeZone
        val coordinates = getCoordinatesByTimeZone(context)
        if (coordinates == null) {
            return coordinates
        }
        props.lat = coordinates.first.toFloat()
        props.lng = coordinates.second.toFloat()
        props.timezone = zoneId
        props.updateNow = false
        val prefs = context.getSharedPreferences(WIDGET_PREF_KEY, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putAppWidgetProps(this, props)
            apply()
        }
        return coordinates
    }

    internal fun getCoordinatesByTimeZone(
        context: Context,
    ): Pair<Double, Double>? {
        // From JSON
        return try {
            val zoneId = TimeZone.getDefault().id
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
            cachedCoordinates
        } catch (_: Exception) {
            null
        }
    }
}
