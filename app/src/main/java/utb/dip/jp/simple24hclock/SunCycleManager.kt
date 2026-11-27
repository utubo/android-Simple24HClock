package utb.dip.jp.simple24hclock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import org.shredzone.commons.suncalc.SunTimes
import utb.dip.jp.simple24hclock.LatLng.getCoordinates
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object SunCycleManager {

    private data class CacheKey(
        val appWidgetId: Int,
        val dayOfYear: Int,
        val zoneId: String,
        val size: Int,
        val lat: Double,
        val lng: Double,
        val colorDayArea: Int,
        val colorNightArea: Int,
    )

    private val cacheByWidgetId = mutableMapOf<Int, Pair<CacheKey, Bitmap>>()

    fun getOrUpdateBackground(
        context: Context,
        props: AppWidgetProps,
        appWidgetId: Int,
        size: Int,
        now: Instant,
        zoneId: ZoneId,
    ): Bitmap {
        val today = now.atZone(zoneId).dayOfYear
        val coordinates = getCoordinates(context, props)
        val key = CacheKey(
            appWidgetId = appWidgetId,
            dayOfYear = today,
            zoneId = zoneId.id,
            size = size,
            lat = coordinates.first,
            lng = coordinates.second,
            colorDayArea = props.colorDayArea,
            colorNightArea = props.colorNightArea,
        )

        val cached = cacheByWidgetId[appWidgetId]
        if (props.updateNow || cached == null || cached.first != key || cached.second.isRecycled) {
            val bitmap = createDayNightBackground(props, size, zoneId, now, coordinates)
            cacheByWidgetId[appWidgetId] = key to bitmap
            return bitmap
        }

        return cached.second
    }

    fun removeCache(appWidgetIds: IntArray?) {
        appWidgetIds?.forEach {
            cacheByWidgetId.remove(it)?.second?.recycle()
        }
    }

    private fun createDayNightBackground(
        props: AppWidgetProps,
        size: Int,
        zoneId: ZoneId,
        now: Instant,
        coordinates: Pair<Double, Double>,
    ): Bitmap {
        val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        val padding = size * 0.05f
        val rect = RectF(padding, padding, size - padding, size - padding)
        val center = size / 2f
        val radius = center - padding

        val (sunrise, sunset) = getSunHours(coordinates, zoneId, now)

        val startAngle = (sunrise * 15f) + 90f
        val endAngle = (sunset * 15f) + 90f
        var sweepAngle = endAngle - startAngle
        if (sweepAngle < 0) sweepAngle += 360f

        paint.color = props.colorNightArea
        canvas.drawCircle(center, center, radius, paint)

        paint.color = props.colorDayArea
        canvas.drawArc(rect, startAngle, sweepAngle, true, paint)

        return bitmap
    }

    private fun getSunHours(
        coordinates: Pair<Double, Double>,
        zoneId: ZoneId,
        now: Instant
    ): Pair<Float, Float> {
        try {
            val targetDate = now.atZone(zoneId).toLocalDate()
            val startOfTargetDay = targetDate.atStartOfDay(zoneId)

            val times = SunTimes.compute()
                .at(coordinates.first, coordinates.second)
                .on(startOfTargetDay)
                .timezone(zoneId.id)
                .execute()

            val rise = times.rise?.toInstant()?.atZone(zoneId)
            val set = times.set?.toInstant()?.atZone(zoneId)

            if (rise != null && set != null) {
                return Pair(rise.toHoursOfDay(), set.toHoursOfDay())
            }
        } catch (_: Exception) {
            // nop
        }
        return Pair(6f, 18f)
    }

    private fun ZonedDateTime.toHoursOfDay(): Float {
        return (hour * 3600 + minute * 60 + second) / 3600f
    }
}