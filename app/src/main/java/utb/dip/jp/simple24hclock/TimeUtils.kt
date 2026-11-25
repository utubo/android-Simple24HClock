package utb.dip.jp.simple24hclock

import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone

object TimeUtils {

    fun resolveZoneId(props: AppWidgetProps): ZoneId {
        val targetId = props.customTimeZone?.trim()?.takeIf { it.isNotEmpty() }
            ?: return ZoneId.systemDefault()

        return try {
            ZoneId.of(targetId)
        } catch (_: Exception) {
            ZoneId.systemDefault()
        }
    }

    fun resolveTimeZone(props: AppWidgetProps): TimeZone {
        return TimeZone.getTimeZone(resolveZoneId(props))
    }

    fun rawInstant(): Instant {
        return Instant.now()
    }

    fun nowCalendar(timeZone: TimeZone): Calendar {
        return Calendar.getInstance(timeZone)
    }

    fun nowCalendar(props: AppWidgetProps): Calendar {
        return nowCalendar(resolveTimeZone(props))
    }

    fun rawCalendar(): Calendar {
        return Calendar.getInstance()
    }
}