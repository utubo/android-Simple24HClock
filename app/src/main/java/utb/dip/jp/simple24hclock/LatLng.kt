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
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.getString("id") == zoneId) {
                    val lat = obj.getDouble("lat")
                    val lng = obj.getDouble("lng")
                    cachedCoordinates = Pair(lat, lng)
                    props.lat = lat.toFloat()
                    props.lng = lng.toFloat()
                    props.updateNow = false
                    val prefs = context.getSharedPreferences(WIDGET_PREF_KEY, Context.MODE_PRIVATE)
                    prefs.edit().apply {
                        putAppWidgetProps(this, props)
                        apply()
                    }
                    break
                }
            }
            cachedCoordinates
        } catch (_: Exception) {
            null
        }
    }
}
