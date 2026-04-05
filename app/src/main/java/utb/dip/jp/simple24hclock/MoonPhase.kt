package utb.dip.jp.simple24hclock

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

object MoonPhase {
    internal fun getMoonPhase(): Int {
        val baseNewMoon = ZonedDateTime.of(2000, 1, 6, 18, 14, 0, 0, ZoneOffset.UTC).toInstant()
        val now = Instant.now()
        val diffSeconds = Duration.between(baseNewMoon, now).seconds
        val diffDays = diffSeconds / 86400.0
        val synodicMonth = 29.530588853
        val moonAge = diffDays % synodicMonth
        return when {
            moonAge < 1.0 -> R.drawable.moon_0
            moonAge < 6.4 -> R.drawable.moon_1
            moonAge < 8.4 -> R.drawable.moon_2
            moonAge < 13.8 -> R.drawable.moon_3
            moonAge < 15.8 -> R.drawable.moon_4
            moonAge < 21.1 -> R.drawable.moon_5
            moonAge < 23.1 -> R.drawable.moon_6
            moonAge < 28.5 -> R.drawable.moon_7
            else -> R.drawable.moon_0
        }
    }
}