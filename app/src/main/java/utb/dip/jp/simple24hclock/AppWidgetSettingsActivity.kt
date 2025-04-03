package utb.dip.jp.simple24hclock

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.TypedValue.COMPLEX_UNIT_PX
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.RemoteViews
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText

const val DEFAULT_TEXT = "\n\n\n\nE  dd"

class AppWidgetSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_app_widget_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // get widget id
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_CANCELED, resultValue)
            finish()
        }
        // views
        val v = object {
            val textNone = findViewById<RadioButton>(R.id.textNone)
            val textDefault = findViewById<RadioButton>(R.id.textDefault)
            val textCustom = findViewById<RadioButton>(R.id.textCustom)
            val textFormat = findViewById<TextInputEditText>(R.id.textFormat)
            val customTextNote = findViewById<TextView>(R.id.custom_text_note)
            val minute = findViewById<CheckBox>(R.id.minute)
            val dayOfYear = findViewById<CheckBox>(R.id.dayOfYear)
            val dayOfYearDots = findViewById<CheckBox>(R.id.dayOfYearDots)
            val preview = findViewById<FrameLayout>(R.id.preview)
        }
        // load prefs
        val prefs = applicationContext.getSharedPreferences(WIDGET_PREF_KEY, Context.MODE_PRIVATE)
        val wp = getAppWidgetProps(prefs, appWidgetId)
        val text = wp.text
        if (text.isNullOrEmpty()) {
            v.textNone.toggle()
        } else if (text == DEFAULT_TEXT) {
            v.textDefault.toggle()
        } else {
            v.textCustom.toggle()
        }
        v.textFormat.setText(wp.format)
        v.minute.isChecked = 0 < wp.minute
        v.dayOfYear.isChecked = 0 < wp.dayOfYear
        v.dayOfYearDots.isChecked = 0 < wp.dayOfYearDots

        // preview
        fun updatePreview() {
            val views = RemoteViews(applicationContext.packageName, R.layout.app_widget)
            views.setTextViewTextSize(R.id.textView, COMPLEX_UNIT_PX, v.textFormat.textSize)
            updateAppWidgetContent(
                views, AppWidgetProps(
                    if (v.minute.isChecked) 0.7F else 0F,
                    if (v.dayOfYear.isChecked) 0.5F else 0F,
                    if (v.dayOfYearDots.isChecked) 0.5F else 0F,
                    if (v.textDefault.isChecked) DEFAULT_TEXT else "",
                    "",
                )
            )
            v.preview.removeAllViews()
            v.preview.addView(views.apply(applicationContext, v.preview))
            v.textFormat.isVisible = v.textCustom.isChecked
        }
        updatePreview()

        // setup events
        findViewById<RadioGroup>(R.id.labelGroup).setOnCheckedChangeListener { _, i ->
            updatePreview()
            if (i == R.id.textCustom) {
                v.textFormat.requestFocus()
            } else {
                v.textFormat.clearFocus()
            }
        }
        v.textFormat.addTextChangedListener {
            v.textCustom.toggle()
        }
        v.customTextNote.movementMethod = LinkMovementMethod.getInstance()
        v.minute.setOnCheckedChangeListener { _, _ -> updatePreview() }
        v.dayOfYear.setOnCheckedChangeListener { _, _ -> updatePreview() }
        v.dayOfYearDots.setOnCheckedChangeListener { _, _ -> updatePreview() }
        findViewById<FloatingActionButton>(R.id.applyButton).setOnClickListener {
            val formatValue = v.textFormat.text.toString()
            val textValue =
                if (v.textDefault.isChecked) DEFAULT_TEXT
                else if (v.textCustom.isChecked) formatValue
                else ""
            prefs.edit().apply {
                putAppWidgetProps(this, appWidgetId, AppWidgetProps(
                    minute = if (v.minute.isChecked) 0.7F else 0F,
                    dayOfYear = if (v.dayOfYear.isChecked) 0.5F else 0F,
                    dayOfYearDots = if (v.dayOfYearDots.isChecked) 0.5F else 0F,
                    text = textValue,
                    format = formatValue,
                ))
                apply()
            }
            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
            updateAppWidget(applicationContext, appWidgetManager, appWidgetId)
            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }
}