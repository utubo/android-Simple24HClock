package utb.dip.jp.simple24hclock

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

object LatLng {

    private val cachedCoordinatesByZone = ConcurrentHashMap<String, Pair<Double, Double>>()

    // Fallback coordinates for "GMT" when not directly listed in timezones.json
    private val gmtCoordinates = Pair(0.0, 0.0)

    internal fun clearCache() {
        cachedCoordinatesByZone.clear()
    }

    internal fun getCoordinates(
        context: Context,
        props: AppWidgetProps,
    ): Pair<Double, Double> {
        // Use explicit fixed coordinates only when both latitude and longitude are valid
        if (props.timeZone == LOCATION_FIXED && !props.lat.isNaN() && !props.lng.isNaN()) {
            return Pair(props.lat.toDouble(), props.lng.toDouble())
        }

        val zoneId = TimeUtils.resolveZoneId(props).id
        Log.d("LatLngDebug", "rawZoneId='$zoneId' -> targetZoneId='$zoneId'")

        // Bypass cached coordinates for this time zone when an immediate refresh is requested
        if (props.updateNow) {
            cachedCoordinatesByZone.remove(zoneId)
        } else {
            // 1. In-memory cache keyed by time zone ID
            cachedCoordinatesByZone[zoneId]?.let { return it }

            // 2. Persisted coordinates in SharedPreferences for the same time zone
            if (props.timeZone == zoneId && !props.lat.isNaN() && !props.lng.isNaN()) {
                val pair = Pair(props.lat.toDouble(), props.lng.toDouble())
                cachedCoordinatesByZone[zoneId] = pair
                return pair
            }
        }

        // 3. Look up coordinates from timezones.json and cache per time zone
        val coordinates = getCoordinatesByTimeZone(context, zoneId)

        props.lat = coordinates.first.toFloat()
        props.lng = coordinates.second.toFloat()
        props.timeZone = zoneId

        val prefs = context.getSharedPreferences(WIDGET_PREF_KEY, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putAppWidgetProps(this, props)
            apply()
        }
        return coordinates
    }

    internal fun getCoordinatesByTimeZone(
        context: Context,
        zoneId: String? = "",
    ): Pair<Double, Double> {
        val targetZoneId = zoneId?.trim()?.takeIf { it.isNotEmpty() } ?: return gmtCoordinates
        cachedCoordinatesByZone[targetZoneId]?.let { return it }

        val resolved = try {
            val jsonString =
                context.assets.open("timezones.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            var matched: Pair<Double, Double>? = null
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                if (obj.optString("id") == targetZoneId && obj.has("lat") && obj.has("lng")) {
                    val lat = obj.getDouble("lat")
                    val lng = obj.getDouble("lng")
                    if (!lat.isNaN() && !lng.isNaN()) {
                        matched = Pair(lat, lng)
                        break
                    }
                }
            }
            matched ?: gmtCoordinates
        } catch (_: Exception) {
            gmtCoordinates
        }

        cachedCoordinatesByZone[targetZoneId] = resolved
        return resolved
    }

    // Load all time zone coordinates from assets/timezones.json once into memory
    @Volatile
    private var allTimeZoneCoordinates: Map<String, Pair<Double, Double>>? = null

    internal fun getAllTimeZoneCoordinates(context: Context): Map<String, Pair<Double, Double>> {
        allTimeZoneCoordinates?.let { return it }

        return synchronized(this) {
            allTimeZoneCoordinates?.let { return it }

            val loaded = LinkedHashMap<String, Pair<Double, Double>>()
            try {
                val jsonString =
                    context.assets.open("timezones.json").bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                    if (obj.has("lat") && obj.has("lng")) {
                        val lat = obj.getDouble("lat")
                        val lng = obj.getDouble("lng")
                        if (!lat.isNaN() && !lng.isNaN()) {
                            loaded[id] = Pair(lat, lng)
                            cachedCoordinatesByZone.putIfAbsent(id, Pair(lat, lng))
                        }
                    }
                }
            } catch (_: Exception) {
                // Keep partial/empty map on read or parse error
            }

            allTimeZoneCoordinates = loaded
            loaded
        }
    }

}

