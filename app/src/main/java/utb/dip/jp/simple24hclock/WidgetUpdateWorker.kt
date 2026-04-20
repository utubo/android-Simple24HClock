package utb.dip.jp.simple24hclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import java.util.Calendar

private const val MIN_ALARM_DELAY_MS = 5000L
fun createIntent(context: Context): PendingIntent {
    val intent = Intent(context, MyBroadcastReceiver::class.java)
    intent.action = INTENT_UPDATE_ALL
    return PendingIntent.getBroadcast(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun setupNext(context: Context) {
    val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val now = System.currentTimeMillis()
    val calendar = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.MINUTE, 1)
    }
    var delayMs = calendar.timeInMillis - now
    if (delayMs < MIN_ALARM_DELAY_MS) {
        delayMs += 60000
    }
    val pendingIntent = createIntent(context)
    manager.cancel(pendingIntent)

    val prefs = context.getSharedPreferences(WIDGET_PREF_KEY, Context.MODE_PRIVATE)
    val useAlarmClock = prefs?.getBoolean("update_alarm_method", false) ?: false
    Log.d("DEBUG", "useAlarmClock: $useAlarmClock")
    if (useAlarmClock) {
        val triggerAtMillisRTC = now + delayMs
        val alarmInfo = AlarmManager.AlarmClockInfo(triggerAtMillisRTC, pendingIntent)
        manager.setAlarmClock(alarmInfo, pendingIntent)
    } else {
        val triggerAtMillis = SystemClock.elapsedRealtime() + delayMs

        if (manager.canScheduleExactAlarms()) {
            // NOTE: Do not use RTC_WAKEUP here as it frequently causes the alarm chain to break.
            manager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            manager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }
}

internal fun startUpdateWorker(context: Context) {
    setupNext(context)
    updateAllAppWidgets(context, AppWidgetManager.getInstance(context))
}

internal fun stopUpdateWorker(context: Context) {
    val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    manager.cancel(createIntent(context))
}
