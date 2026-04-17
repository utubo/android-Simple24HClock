package utb.dip.jp.simple24hclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
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
    val triggerAtMillis = SystemClock.elapsedRealtime() + delayMs
    val pendingIntent = createIntent(context)
    manager.cancel(pendingIntent)
    // NOTE: Do not use RTC_WAKEUP here as it frequently causes the alarm chain to break.
    if (manager.canScheduleExactAlarms()) {
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

internal fun doWork(context: Context) {
    setupNext(context)
    updateAllAppWidgets(context, AppWidgetManager.getInstance(context))
}

internal fun restart(context: Context): Boolean {
    doWork(context)
    return true
}

internal fun stop(context: Context) {
    val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    manager.cancel(createIntent(context))
}
