package utb.dip.jp.simple24hclock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

class MyAppWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = context.getSharedPreferences(WIDGET_IDS_KEY, Context.MODE_PRIVATE)
        prefs.edit().apply {
            this.clear()
            for (appWidgetId in appWidgetIds) {
                putInt("widget_$appWidgetId", appWidgetId)
            }
            apply()
        }
        val layoutPrefs = context.getSharedPreferences(WIDGET_LAYOUT_KEY, Context.MODE_PRIVATE)
        layoutPrefs.edit().apply {
            this.clear()
            for (appWidgetId in appWidgetIds) {
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                calculateLayout(this, appWidgetId, options)
            }
            apply()
        }
        restart(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        restart(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        stop(context)
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        super.onDeleted(context, appWidgetIds)
        cleanupPrefs(context, appWidgetIds)
    }

    override fun onRestored(context: Context?, oldWidgetIds: IntArray?, newWidgetIds: IntArray?) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        if (context == null) return
        val widgetPrefs = context.getSharedPreferences(WIDGET_PREF_KEY, Context.MODE_PRIVATE)
        val oldPrefs = widgetPrefs.all.toMap()
        val newWidgetIdsSize = newWidgetIds?.size ?: 0
        widgetPrefs.edit().apply {
            oldPrefs.forEach { (key, value) ->
                val id = key.split("_").last().toIntOrNull() ?: -1
                val i = oldWidgetIds?.indexOf(id) ?: -1
                if (i in 0..<newWidgetIdsSize) {
                    remove(key)
                    val newId = newWidgetIds?.get(i)
                    val newKey = key.replace("_$id", "_$newId")
                    when (value) {
                        is Int -> putInt(newKey, value)
                        is Float -> putFloat(newKey, value)
                        is String -> putString(newKey, value)
                        is Boolean -> putBoolean(newKey, value)
                    }
                }
            }
            apply()
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        id: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, id, newOptions)
        val layoutPrefs = context.getSharedPreferences(WIDGET_LAYOUT_KEY, Context.MODE_PRIVATE)
        layoutPrefs.edit().apply {
            calculateLayout(this, id, newOptions)
            apply()
        }
        updateAppWidget(context, appWidgetManager, id)
    }
}

internal fun updateAllAppWidgets(
    context: Context,
    appWidgetManager: AppWidgetManager
) {
    val prefs = context.getSharedPreferences(WIDGET_IDS_KEY, Context.MODE_PRIVATE)
    prefs.all.forEach { (_, id) ->
        updateAppWidget(context, appWidgetManager, id as Int)
    }
}

internal fun cleanupPrefs(context: Context?, deletedIds: IntArray?) {
    if (context == null) return
    val idsPref = context.getSharedPreferences(WIDGET_IDS_KEY, Context.MODE_PRIVATE)
    if (deletedIds != null) {
        idsPref.edit().apply {
            deletedIds.forEach { id ->
                remove("widget_$id")
            }
            apply()
        }
    }
    val ids = mutableSetOf<Int>()
    idsPref.all.forEach { (_, id) ->
        ids.add(id as Int)
    }
    val prefs = context.getSharedPreferences(WIDGET_PREF_KEY, Context.MODE_PRIVATE)
    prefs.edit().apply {
        prefs.all.forEach { (key, _) ->
            val id = key.split('_').last().toIntOrNull()
            if (id != null && !ids.contains(id)) {
                remove(key)
            }
        }
        apply()
    }
}

