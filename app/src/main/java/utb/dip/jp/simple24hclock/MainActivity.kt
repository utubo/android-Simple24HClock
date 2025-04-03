package utb.dip.jp.simple24hclock

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Add the widget to home screen
        val context = this.applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val myProvider = ComponentName(context, MyAppWidgetProvider::class.java)
        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            appWidgetManager.requestPinAppWidget(myProvider, null, null)
        }
        finish()
    }
}