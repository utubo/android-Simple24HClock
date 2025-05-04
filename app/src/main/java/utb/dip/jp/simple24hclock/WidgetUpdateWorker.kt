package utb.dip.jp.simple24hclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import java.util.Calendar
import java.util.Date

fun createIntent(context: Context): PendingIntent {
    val intent = Intent(context, MyBroadcastReceiver::class.java)
    intent.setAction(INTENT_UPDATE_ALL)
    return PendingIntent.getBroadcast(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun setupNext(context: Context) {
    val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val next = Calendar.getInstance()
    next.set(Calendar.MILLISECOND, 0)
    next.set(Calendar.SECOND, 0)
    next.add(Calendar.MINUTE, 1)
    val pendingIntent = createIntent(context)
    manager.cancel(pendingIntent)
    manager.set(
        AlarmManager.RTC,
        next.time.time,
        createIntent(context)
    )
}

internal fun doWork(context: Context) {
    updateAllAppWidgets(context, AppWidgetManager.getInstance(context))
    setupNext(context)
}

internal fun restart(context: Context): Boolean {
    // Debounce
    val workerPrefs = context.getSharedPreferences(WIDGET_WORKER_KEY, Context.MODE_PRIVATE)
    val lastTick = workerPrefs.getLong("last_restart_time", 0L)
    val now = Date().time
    if (lastTick != 0L && now - lastTick < 100) {
        return false
    }
    workerPrefs.edit().apply {
        putLong("last_restart_time", now)
        apply()
    }
    // start
    doWork(context)
    return true
}

internal fun stop(context: Context) {
    val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    manager.cancel(createIntent(context))
}
