package utb.dip.jp.simple24hclock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class WidgetUpdateForegroundService : Service() {

    companion object {
        private var isRunning = false

        fun isServiceRunning(): Boolean {
            return isRunning
        }

        fun toggle(context: Context) {
            val prefs = context.getSharedPreferences(WIDGET_PREF_KEY, MODE_PRIVATE)
            val useFGS = prefs.getBoolean("use_fgs", false)
            val serviceIntent = Intent(context, WidgetUpdateForegroundService::class.java)
            if (useFGS) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.stopService(serviceIntent)
            }
        }
    }

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_TIME_TICK) {
                Log.d("FGS", "ACTION_TIME_TICK received")
                val updateIntent = Intent(context, MyBroadcastReceiver::class.java).apply {
                    action = INTENT_UPDATE_ALL
                }
                context.sendBroadcast(updateIntent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_TIME_TICK)
        registerReceiver(tickReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
        isRunning = true

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
        unregisterReceiver(tickReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val channelId = "widget_update_channel"
        val channel =
            NotificationChannel(channelId, "時計の安定化", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notify_text))
            .setSmallIcon(R.drawable.ic_notify)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

}
