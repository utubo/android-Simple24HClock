package utb.dip.jp.simple24hclock

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.util.TypedValue.COMPLEX_UNIT_PX
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.get
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import utb.dip.jp.simple24hclock.databinding.ActivitySettingsBinding


const val DEFAULT_TEXT = "\n\n\n\nE  dd"

class SettingsActivity : FragmentActivity() {

    @SuppressLint("QueryPermissionsNeeded", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Views
        val v = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(v.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fl_settings_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // color tip
        val colors = hashMapOf<String, Int>()
        var selectedPart = ""
        val masterPartNames = resources.getStringArray(R.array.part_names).toList()
        val masterPartKeys = resources.getStringArray(R.array.part_keys).toList()
        val partNames = mutableListOf<String>()
        val partKeys = mutableListOf<String>()
        partNames.addAll(masterPartNames)
        partKeys.addAll(masterPartKeys)
        var colorChipAdapter: PartsAdapter? = null
        fun toggleColorPart(key: String, isVisible: Boolean) {
            val masterIndex = masterPartKeys.indexOf(key)
            if (masterIndex == -1) return
            if (isVisible) {
                if (!partKeys.contains(key)) {
                    val insertIndex =
                        masterPartKeys.take(masterIndex).count { partKeys.contains(it) }
                    partNames.add(insertIndex, masterPartNames[masterIndex])
                    partKeys.add(insertIndex, key)
                    colorChipAdapter?.notifyItemInserted(insertIndex)
                }
            } else {
                if (selectedPart == key) {
                    selectedPart = ""
                    colorChipAdapter?.resetSelection()
                }
                val removeIndex = partKeys.indexOf(key)
                if (removeIndex != -1) {
                    partNames.removeAt(removeIndex)
                    partKeys.removeAt(removeIndex)
                    colorChipAdapter?.notifyItemRemoved(removeIndex)
                }
            }
        }
        v.llSettingsMain.setOnClickListener { colorChipAdapter?.resetSelection() }
        v.llSettings.setOnClickListener { colorChipAdapter?.resetSelection() }

        // other items
        // NOTE: initialize on load prefs.
        // var backgroundAlpha: Float
        // var tapBehavior: String
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

        // load prefs
        val prefs = applicationContext.getSharedPreferences(WIDGET_PREF_KEY, MODE_PRIVATE)
        val wp = getAppWidgetProps(prefs, appWidgetId)
        when (wp.text) {
            null, "" -> v.rbLabelNone.toggle()
            DEFAULT_TEXT -> v.rbLabelRecommended.toggle()
            else -> v.rbLabelCustom.toggle()
        }
        v.etFormat.setText(wp.format)
        v.rgRotate.check(
            when (wp.rotate) {
                180F -> R.id.rb_rotate_moon_top
                ROTATE_AUTO -> R.id.rb_rotate_auto
                ROTATE_FIX_HOUR_HAND -> R.id.rb_rotate_fix_hour_hand
                else -> R.id.rb_rotate_sun_top
            }
        )
        v.cbMinute.isChecked = 0 < wp.minute
        v.cbMinuteAndHourDots.isChecked = 0 < wp.minuteAndHourDots
        v.cbDayOfYear.isChecked = 0 < wp.dayOfYear
        v.cbMonthDots.isChecked = 0 < wp.dayOfYearDots
        v.cbMoonPhase.isChecked = wp.moonPhase
        var backgroundAlpha = wp.backgroundAlpha
        // NOTE: DON'T USE libs.kotlin.reflect.
        colors["colorHour"] = wp.colorHour
        colors["colorMinute"] = wp.colorMinute
        colors["colorDayOfYear"] = wp.colorDayOfYear
        colors["colorBorder"] = wp.colorBorder
        colors["colorDots"] = wp.colorDots
        colors["colorMinuteDots"] = wp.colorMinuteDots
        colors["colorDayOfYearDots"] = wp.colorDayOfYearDots
        colors["colorSun"] = wp.colorSun
        colors["colorMoon"] = wp.colorMoon
        colors["colorDayArea"] = wp.colorDayArea
        colors["colorNightArea"] = wp.colorNightArea
        colors["colorText"] = wp.colorText
        var tapBehavior = wp.tapBehavior ?: ""
        v.tvTapBehavior.text = when (tapBehavior) {
            "" -> getString(R.string.none)
            "alarm" -> getString(R.string.alarm)
            "calendar" -> getString(R.string.calendar)
            else -> wp.tapBehaviorLabel
        }

        // create AppWidgetProps for preview and save
        fun newAppWidgetProps(): AppWidgetProps {
            val wp = AppWidgetProps(
                id = appWidgetId,
                minute = if (v.cbMinute.isChecked) 1F else 0F,
                minuteAndHourDots = if (v.cbMinuteAndHourDots.isChecked) 1F else 0F,
                dayOfYear = if (v.cbDayOfYear.isChecked) 1F else 0F,
                dayOfYearDots = if (v.cbMonthDots.isChecked) 1F else 0F,
                text = if (v.rbLabelRecommended.isChecked) DEFAULT_TEXT else "",
                format = "",
                tapBehavior = "",
                backgroundAlpha = backgroundAlpha,
                rotate = when (v.rgRotate.checkedRadioButtonId) {
                    R.id.rb_rotate_moon_top -> 180F
                    R.id.rb_rotate_auto -> ROTATE_AUTO
                    R.id.rb_rotate_fix_hour_hand -> ROTATE_FIX_HOUR_HAND
                    else -> 0F
                },
                moonPhase = v.cbMoonPhase.isChecked,
                updateNow = true,
            )
            wp.colorHour = colors["colorHour"] ?: -1
            wp.colorMinute = colors["colorMinute"] ?: -1
            wp.colorDayOfYear = colors["colorDayOfYear"] ?: -1
            wp.colorBorder = colors["colorBorder"] ?: -1
            wp.colorDots = colors["colorDots"] ?: -1
            wp.colorMinuteDots = colors["colorMinuteDots"] ?: -1
            wp.colorDayOfYearDots = colors["colorDayOfYearDots"] ?: -1
            wp.colorSun = colors["colorSun"] ?: -1
            wp.colorMoon = colors["colorMoon"] ?: -1
            wp.colorDayArea = colors["colorDayArea"] ?: 0
            wp.colorNightArea = colors["colorNightArea"] ?: 0
            wp.colorText = colors["colorText"] ?: -1
            return wp
        }

        fun updatePreview() {
            val views = RemoteViews(applicationContext.packageName, R.layout.app_widget)
            views.setTextViewTextSize(R.id.tv_label, COMPLEX_UNIT_PX, v.etFormat.textSize)
            updateAppWidgetContent(
                applicationContext,
                views,
                newAppWidgetProps(),
                resources.getDimension(R.dimen.preview_size)
            )
            v.preview.removeAllViews()
            v.preview.addView(views.apply(applicationContext, v.preview))
            v.etFormat.isVisible = v.rbLabelCustom.isChecked
            toggleColorPart("colorMinute", v.cbMinute.isChecked)
            toggleColorPart("colorMinuteDots", v.cbMinuteAndHourDots.isChecked)
            toggleColorPart("colorDayOfYear", v.cbDayOfYear.isChecked)
            toggleColorPart("colorDayOfYearDots", v.cbMonthDots.isChecked)
            toggleColorPart("colorText", v.rbLabelRecommended.isChecked)
        }
        updatePreview()

        // setup events
        v.rgLabel.setOnCheckedChangeListener { _, i ->
            updatePreview()
            if (i == R.id.rb_label_custom) {
                v.etFormat.requestFocus()
            } else {
                v.etFormat.clearFocus()
            }
        }
        v.etFormat.addTextChangedListener {
            v.rbLabelCustom.toggle()
        }
        v.tvLabelNote.movementMethod = LinkMovementMethod.getInstance()
        v.rgRotate.setOnCheckedChangeListener { _, _ -> updatePreview() }
        v.cbMinute.setOnCheckedChangeListener { _, _ -> updatePreview() }
        v.cbMinuteAndHourDots.setOnCheckedChangeListener { _, _ -> updatePreview() }
        v.cbDayOfYear.setOnCheckedChangeListener { _, _ -> updatePreview() }
        v.cbMonthDots.setOnCheckedChangeListener { _, _ -> updatePreview() }
        v.cbMoonPhase.setOnCheckedChangeListener { _, _ -> updatePreview() }
        v.sbAlpha.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
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
            v.sbRed.progress = Color.red(argb)
            v.sbGreen.progress = Color.green(argb)
            v.sbBlue.progress = Color.blue(argb)
            if (!alpha) {
                // NOP
            } else if (key.endsWith("Area")) {
                v.sbAlpha.progress = (backgroundAlpha * 255F).toInt()
            } else {
                v.sbAlpha.progress = Color.alpha(argb)
            }
            selectedPart = key
        }

        fun updateColor() {
            if (selectedPart == "") return
            var alpha = v.sbAlpha.progress
            if (selectedPart.endsWith("Area")) {
                backgroundAlpha = v.sbAlpha.progress.toFloat() / 255
                alpha = 255
            }
            colors[selectedPart] = Color.argb(
                alpha,
                v.sbRed.progress,
                v.sbGreen.progress,
                v.sbBlue.progress
            )
            updatePreview()
        }

        arrayOf(v.sbAlpha, v.sbRed, v.sbGreen, v.sbBlue).forEach {
            it.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    updateColor()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
        colorChipAdapter = PartsAdapter(partNames, partKeys) { selectedKey ->
            selectedPart = selectedKey
            if (selectedPart != "") {
                val argb = colors[selectedPart] ?: 0
                setupARGBSeekBars(argb, true)
                v.tvOpacity.text = getString(
                    when (selectedPart) {
                        "colorDayArea" -> R.string.linked_with_night
                        "colorNightArea" -> R.string.linked_with_day
                        else -> R.string.opacity
                    }
                )
            }
        }
        v.rvPartsSelector.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        v.rvPartsSelector.adapter = colorChipAdapter

        // Pallet
        val palletBitmap by lazy {
            v.ivPallet.drawable?.toBitmap()
        }
        v.ivPallet.setOnTouchListener { view, event ->
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
            if (Color.alpha(pixel) != 0) {
                setupARGBSeekBars(pixel, false)
                updateColor()
                view.performClick()
            }
            true
        }

        // Tap behavior
        val appPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.component?.let {
                    tapBehavior = it.flattenToString()
                    v.tvTapBehavior.text =
                        getAppLabel(this@SettingsActivity, it.packageName)
                }
            }
        }

        val tapBehaviors = arrayOf(
            getString(R.string.none),
            getString(R.string.alarm),
            getString(R.string.calendar),
            getString(R.string.other_apps),
        )
        v.tvTapBehavior.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.tap_behavior)
                .setItems(tapBehaviors) { _, which ->
                    v.tvTapBehavior.text = tapBehaviors[which]
                    tapBehavior = when (which) {
                        0 -> ""
                        1 -> "alarm"
                        2 -> "calendar"
                        else -> "app"
                    }
                    if (tapBehavior == "app") {
                        val intent = Intent(Intent.ACTION_PICK_ACTIVITY).apply {
                            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                                addCategory(Intent.CATEGORY_LAUNCHER)
                            }
                            putExtra(Intent.EXTRA_INTENT, mainIntent)
                            putExtra(Intent.EXTRA_TITLE, getString(R.string.tap_behavior))
                        }
                        appPickerLauncher.launch(intent)
                    }
                }
                .show()
        }

        // Apply
        v.btnApply.setOnClickListener {
            val formatValue = v.etFormat.text.toString()
            val textValue =
                if (v.rbLabelRecommended.isChecked) DEFAULT_TEXT
                else if (v.rbLabelCustom.isChecked) formatValue
                else ""
            prefs.edit().apply {
                val wp = newAppWidgetProps()
                wp.text = textValue
                wp.format = formatValue
                wp.tapBehavior = tapBehavior
                wp.tapBehaviorLabel = v.tvTapBehavior.text.toString()
                putAppWidgetProps(this, wp)
                apply()
            }
            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
            updateAppWidget(applicationContext, appWidgetManager, appWidgetId)
            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }

        v.tvPreventTimeLag.setOnClickListener {
            if (!canScheduleExact()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
            if (!isIgnoringBatteryOpt()) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${applicationContext.packageName}")
                }
                startActivity(intent)
            }
        }
        updatePreventTimeLagState()

        v.tvLicenses.setOnClickListener {
            val intent = Intent(this, OssLicenseActivity::class.java)
            startActivity(intent)
        }

        fun updateDonationLabel() {
            val donationCount = DonationHelper.getDonationCount(this)
            v.tvSupport.text = getString(R.string.donation_star).repeat(donationCount)
        }
        updateDonationLabel()
        v.tvSupport.setOnClickListener {
            DonationHelper.start(this) {
                updateDonationLabel()
            }
        }
    }

    fun getAppLabel(context: Context, packageName: String?): String {
        if (packageName == null) return getString(R.string.none)
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            getString(R.string.other_apps)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val llSettingsMain = findViewById<LinearLayout>(R.id.ll_settings_main)
        llSettingsMain.orientation =
            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                LinearLayout.HORIZONTAL
            } else {
                LinearLayout.VERTICAL
            }
    }

    override fun onResume() {
        super.onResume()
        updatePreventTimeLagState()
    }

    fun updatePreventTimeLagState() {
        val icon =
            if (canScheduleExact() && isIgnoringBatteryOpt()) R.drawable.ic_check_circle else R.drawable.ic_warn_circle
        val tvPreventTimeLag =
            findViewById<androidx.appcompat.widget.AppCompatTextView>(R.id.tv_prevent_time_lag)
        tvPreventTimeLag.setCompoundDrawablesWithIntrinsicBounds(0, 0, icon, 0)
    }

    fun canScheduleExact(): Boolean {
        val manager = getSystemService(ALARM_SERVICE) as AlarmManager
        return manager.canScheduleExactAlarms()
    }

    fun isIgnoringBatteryOpt(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }
}