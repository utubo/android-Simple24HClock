package utb.dip.jp.simple24hclock

import android.appwidget.AppWidgetManager
import android.content.Context


internal fun resetUpdateTrigger(context: Context) {
    WidgetUpdateForegroundService.toggle(context)
    restart(context)
}

internal fun restart(context: Context) {
    try {
        if (!WidgetUpdateForegroundService.isServiceRunning()) {
            startUpdateWorker(context)
        }
        startWatchdogWorker(context)
    } finally {
        updateAllAppWidgets(context, AppWidgetManager.getInstance(context))
    }
}

internal fun stop(context: Context) {
    stopUpdateWorker(context)
    stopWatchdogWorker(context)
}
