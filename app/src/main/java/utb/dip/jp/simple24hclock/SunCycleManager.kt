package utb.dip.jp.simple24hclock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import org.shredzone.commons.suncalc.SunTimes
import utb.dip.jp.simple24hclock.LatLng.getCoordinates
import java.util.Calendar
import java.util.TimeZone

object SunCycleManager {

    // Cache to minimize CPU/Memory usage during 1-minute updates
    private var cachedBackground: Bitmap? = null
    private var lastUpdateDay = -1

    /**
     * Returns a high-quality background bitmap.
     * Re-generates only when the date changes or the widget is resized.
     */
    fun getOrUpdateBackground(context: Context, props: AppWidgetProps, size: Int): Bitmap {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (props.updateNow || cachedBackground == null || lastUpdateDay != today || cachedBackground?.width != size) {
            cachedBackground = createDayNightBackground(context, props, size)
            lastUpdateDay = today
        }

        return cachedBackground!!
    }

    private fun createDayNightBackground(
        context: Context,
        props: AppWidgetProps,
        size: Int,
    ): Bitmap {
        val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        // Apply 5% padding
        val padding = size * 0.05f
        val rect = RectF(padding, padding, size - padding, size - padding)
        val center = size / 2f
        val radius = center - padding

        // 1. Get sunrise and sunset times
        val (sunrise, sunset) = getSunHours(context, props)

        // 2. Calculate angles (24h clock: 00:00 at bottom +90deg)
        val startAngle = (sunrise * 15f) + 90f
        val endAngle = (sunset * 15f) + 90f
        var sweepAngle = endAngle - startAngle
        if (sweepAngle < 0) sweepAngle += 360f

        // 3. Draw Background Layers
        // Night area
        paint.color = props.colorNightArea
        canvas.drawCircle(center, center, radius, paint)

        // Day area
        paint.color = props.colorDayArea
        canvas.drawArc(rect, startAngle, sweepAngle, true, paint)

        return bitmap
    }

    private fun getSunHours(context: Context, props: AppWidgetProps): Pair<Float, Float> {
        val coordinates = getCoordinates(context, props)
        val zoneId = TimeZone.getDefault().id
        return if (coordinates != null) {
            val times = SunTimes.compute()
                .at(coordinates.first, coordinates.second)
                .timezone(zoneId)
                .today()
                .execute()

            val rise = times.rise?.toInstant()?.atZone(java.time.ZoneId.of(zoneId))
            val set = times.set?.toInstant()?.atZone(java.time.ZoneId.of(zoneId))

            if (rise != null && set != null) {
                Pair(rise.hour + rise.minute / 60f, set.hour + set.minute / 60f)
            } else {
                Pair(6f, 18f) // Default fallback
            }
        } else {
            Pair(6f, 18f) // Default fallback
        }
    }
}
