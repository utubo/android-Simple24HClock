package utb.dip.jp.simple24hclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import java.util.Date

fun createIntent(context: Context): PendingIntent {
    val intent = Intent(context, MyAppWidgetProvider::class.java)
    intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
    intent.putExtra("FROM_ALARM", true)
    return PendingIntent.getBroadcast(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

internal fun setupNext(context: Context) {
    val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val now = Date().time
    val interval = 60_000L
    val next = now + (interval - now % interval)
    val pendingIntent = createIntent(context)
    manager.cancel(pendingIntent)
    manager.set(
        AlarmManager.RTC,
        next,
        createIntent(context)
    )
}

internal fun restart(context: Context) {
    updateAllAppWidgets(context, AppWidgetManager.getInstance(context))
    setupNext(context)
}

internal fun stop(context: Context) {
    val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    manager.cancel(createIntent(context))
}
