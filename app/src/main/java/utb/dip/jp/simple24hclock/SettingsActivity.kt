package utb.dip.jp.simple24hclock

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.util.TypedValue.COMPLEX_UNIT_PX
import android.view.MotionEvent
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.RemoteViews
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.get
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.FragmentActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.memberProperties


const val DEFAULT_TEXT = "\n\n\n\nE  dd"

class SettingsActivity : FragmentActivity() {
    @SuppressLint("QueryPermissionsNeeded", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val partNames = resources.getStringArray(R.array.part_names)
        val partKeys = resources.getStringArray(R.array.part_keys)
        var selectedPart = ""
        val colors = hashMapOf<String, Int>()
        val prop = AppWidgetProps::class.memberProperties
        var backgroundAlpha: Float

        setContentView(R.layout.activity_settings)
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
            setResult(RESULT_CANCELED, resultValue)
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
            val moonPhase = findViewById<CheckBox>(R.id.moonPhase)
            val preview = findViewById<FrameLayout>(R.id.preview)
            val rotateRadioGroup = findViewById<RadioGroup>(R.id.rotateRadioGroup)
            val tapRadioGroup = findViewById<RadioGroup>(R.id.tapRadioGroup)
            val colorsBtn = findViewById<TextView>(R.id.ColorsBtn)
            val redBar = findViewById<SeekBar>(R.id.redBar)
            val greenBar = findViewById<SeekBar>(R.id.greenBar)
            val blueBar = findViewById<SeekBar>(R.id.blueBar)
            val alphaBar = findViewById<SeekBar>(R.id.alphaBar)
            var opacityTextView = findViewById<TextView>(R.id.opacityTextView)
        }
        // load prefs
        val prefs = applicationContext.getSharedPreferences(WIDGET_PREF_KEY, MODE_PRIVATE)
        val wp = getAppWidgetProps(prefs, appWidgetId)
        when (wp.text) {
            null, "" -> v.textNone.toggle()
            DEFAULT_TEXT -> v.textDefault.toggle()
            else -> v.textCustom.toggle()
        }
        v.textFormat.setText(wp.format)
        v.rotateRadioGroup.check(
            when (wp.rotate) {
                180F -> R.id.rotateMoonTop
                -1F -> R.id.rotateAuto
                else -> R.id.rotateSunTop
            }
        )
        v.minute.isChecked = 0 < wp.minute
        v.dayOfYear.isChecked = 0 < wp.dayOfYear
        v.dayOfYearDots.isChecked = 0 < wp.dayOfYearDots
        v.moonPhase.isChecked = wp.moonPhase
        v.tapRadioGroup.check(
            when (wp.tapBehavior) {
                "alarm" -> R.id.tapAlarm
                "calendar" -> R.id.tapCalendar
                else -> R.id.tapNone
            }
        )
        backgroundAlpha = wp.backgroundAlpha
        partKeys.forEach { partKey ->
            colors[partKey] = prop.find { it.name == partKey }?.get(wp) as Int
        }

        // create AppWidgetProps for preview and save
        fun newAppWidgetProps(): AppWidgetProps {
            val wp = AppWidgetProps(
                id = appWidgetId,
                minute = if (v.minute.isChecked) 1F else 0F,
                dayOfYear = if (v.dayOfYear.isChecked) 1F else 0F,
                dayOfYearDots = if (v.dayOfYearDots.isChecked) 0.5F else 0F,
                text = if (v.textDefault.isChecked) DEFAULT_TEXT else "",
                format = "",
                tapBehavior = "",
                backgroundAlpha = backgroundAlpha,
                rotate = when (v.rotateRadioGroup.checkedRadioButtonId) {
                    R.id.rotateMoonTop -> 180F
                    R.id.rotateAuto -> -1F
                    else -> 0F
                },
                moonPhase = v.moonPhase.isChecked,
                updateNow = true,
            )
            partKeys.forEach { key ->
                (prop.find { it.name == key } as KMutableProperty<*>).setter.call(
                    wp,
                    colors[key]
                )
            }
            return wp
        }

        // preview
        fun updatePreview() {
            val views = RemoteViews(applicationContext.packageName, R.layout.app_widget)
            views.setTextViewTextSize(R.id.textView, COMPLEX_UNIT_PX, v.textFormat.textSize)
            updateAppWidgetContent(applicationContext, views, newAppWidgetProps())
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
        v.rotateRadioGroup.setOnCheckedChangeListener { _, _ -> updatePreview() }
        v.minute.setOnCheckedChangeListener { _, _ -> updatePreview() }
        v.dayOfYear.setOnCheckedChangeListener { _, _ -> updatePreview() }
        v.dayOfYearDots.setOnCheckedChangeListener { _, _ -> updatePreview() }
        v.moonPhase.setOnCheckedChangeListener { _, _ -> updatePreview() }
        v.alphaBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updatePreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }
        })

        // Color
        fun setupARGBSeekBars(argb: Int, alpha: Boolean) {
            val key = selectedPart
            selectedPart = "" // prevent event
            v.redBar.progress = Color.red(argb)
            v.greenBar.progress = Color.green(argb)
            v.blueBar.progress = Color.blue(argb)
            if (!alpha) {
                // NOP
            } else if (key.endsWith("Area")) {
                v.alphaBar.progress = (backgroundAlpha * 255F).toInt()
            } else {
                v.alphaBar.progress = Color.alpha(argb)
            }
            selectedPart = key
        }
        findViewById<TextView>(R.id.ColorsBtn).setOnClickListener {
            val names = partNames.toMutableList()
            if (!v.minute.isChecked) names.remove(partNames[partKeys.indexOf("colorMinute")])
            if (!v.dayOfYear.isChecked) names.remove(partNames[partKeys.indexOf("colorDayOfYear")])
            if (!v.dayOfYearDots.isChecked) names.remove(partNames[partKeys.indexOf("colorDayOfYearDots")])
            if (!v.textDefault.isChecked) names.remove(partNames[partKeys.indexOf("colorText")])
            AlertDialog.Builder(this)
                .setTitle(R.string.select_part)
                .setItems(names.toTypedArray()) { _, which ->
                    val name = names[which]
                    selectedPart = partKeys[partNames.indexOf(name)]
                    val argb = colors[selectedPart] ?: 0
                    setupARGBSeekBars(argb, true)
                    v.colorsBtn.text = name
                    v.opacityTextView.text = getString(
                        when (selectedPart) {
                            "colorDayArea" -> R.string.linked_to_night
                            "colorNightArea" -> R.string.linked_to_day
                            else -> R.string.opacity
                        }
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        fun updateColor() {
            if (selectedPart == "") return
            var alpha = v.alphaBar.progress
            if (selectedPart.endsWith("Area")) {
                backgroundAlpha = v.alphaBar.progress.toFloat() / 255
                alpha = 255
            }
            colors[selectedPart] = Color.argb(
                alpha,
                v.redBar.progress,
                v.greenBar.progress,
                v.blueBar.progress
            )
            updatePreview()
        }
        arrayOf(v.alphaBar, v.redBar, v.greenBar, v.blueBar).forEach {
            it.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    updateColor()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
        // Pallet
        val themeBackgroundColor by lazy {
            TypedValue().let {
                theme.resolveAttribute(android.R.attr.colorBackground, it, true)
                it.data
            }
        }
        val palletBitmap by lazy {
            findViewById<ImageView>(R.id.PalletImageView).drawable?.toBitmap()
        }
        findViewById<ImageView>(R.id.PalletImageView).setOnTouchListener { view, event ->
            if (selectedPart.isEmpty() || event.action != MotionEvent.ACTION_DOWN) return@setOnTouchListener false
            val bitmap = palletBitmap ?: return@setOnTouchListener false
            val iv = view as ImageView
            val xx = event.x - iv.paddingLeft
            val yy = event.y - iv.paddingTop
            val inverse = Matrix()
            iv.imageMatrix.invert(inverse)
            val points = floatArrayOf(xx, yy)
            inverse.mapPoints(points)
            val x = points[0].toInt()
            val y = points[1].toInt()
            if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) return@setOnTouchListener false
            val pixel = bitmap[x, y]
            val argb = if (Color.alpha(pixel) == 0) themeBackgroundColor else pixel
            setupARGBSeekBars(argb, false)
            updateColor()
            view.performClick()
            true
        }

        // Apply
        findViewById<FloatingActionButton>(R.id.applyButton).setOnClickListener {
            val formatValue = v.textFormat.text.toString()
            val textValue =
                if (v.textDefault.isChecked) DEFAULT_TEXT
                else if (v.textCustom.isChecked) formatValue
                else ""
            prefs.edit().apply {
                val wp = newAppWidgetProps()
                wp.text = textValue
                wp.format = formatValue
                wp.tapBehavior = when (v.tapRadioGroup.checkedRadioButtonId) {
                    R.id.tapAlarm -> "alarm"
                    R.id.tapCalendar -> "calendar"
                    else -> ""
                }
                putAppWidgetProps(this, wp)
                apply()
            }
            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
            updateAppWidget(applicationContext, appWidgetManager, appWidgetId)
            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }

        findViewById<TextView>(R.id.licenses).setOnClickListener {
            val intent = Intent(this, OssLicenseActivity::class.java)
            startActivity(intent)
        }
    }
}