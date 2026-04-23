package utb.dip.jp.simple24hclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MyBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            INTENT_UPDATE_ALL,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_MY_PACKAGE_UNSUSPENDED,
            Intent.ACTION_BATTERY_OKAY -> restart(context)

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> resetUpdateTrigger(context)

            // Intent.ACTION_SCREEN_OFF -> stop(context)
        }
    }
}
