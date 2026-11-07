package utb.dip.jp.simple24hclock

import android.Manifest
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
import android.os.Build
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.get
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import utb.dip.jp.simple24hclock.databinding.ActivitySettingsBinding
import androidx.core.net.toUri
import androidx.core.view.children
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale
import java.util.TimeZone

// --- Constants (Eliminating Magic Numbers) ---
private const val DEFAULT_TEXT = "\n\n\n\nE  dd"
private const val REQ_CODE_NOTIFICATION_PERMISSION = 101
private const val TAP_BEHAVIOR_NONE = ""
private const val INVALID_DATE_FORMAT_LETTERS = "BCIJNPSTUVYZbfhijklnorstux"

class SettingsActivity : FragmentActivity() {

    private lateinit var v: ActivitySettingsBinding
    private lateinit var prefs: android.content.SharedPreferences

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedPart = ""
    private var textLocale = ""
    private var backgroundAlpha = 1.0f
    private var tapBehavior = TAP_BEHAVIOR_NONE

    private val colors = hashMapOf<String, Int>()
    private var colorChipAdapter: PartsAdapter? = null

    private lateinit var masterPartNames: List<String>
    private lateinit var masterPartKeys: List<String>
    private val partNames = mutableListOf<String>()
    private val partKeys = mutableListOf<String>()
    private val systemLocales by lazy {
        Locale.getAvailableLocales().filter { it.language.isNotEmpty() }
            .distinctBy { it.language }.sortedBy { it.getDisplayLanguage(Locale.getDefault()) }
    }
    private val localeNames by lazy {
        mutableListOf(getString(R.string.system_default)).apply {
            addAll(systemLocales.map { it.getDisplayLanguage(Locale.getDefault()) })
        }
    }

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            fetchAndFillCurrentLocation()
        }
    }

    @SuppressLint("QueryPermissionsNeeded", "ClickableViewAccessibility", "BatteryLife")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        v = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(v.root)
        enableEdgeToEdge()

        setupWindowInsets()
        initWidgetIdAndPrefs()
        initMasterParts()
        loadWidgetPropsToUI()

        // Setup individual UI sections
        setupPreviewListeners()
        setupColorPalette()
        setupLocationSettings()
        setupTapBehavior()
        setupPerformanceSettings()
        setupSupportAndLicenses()
        setupApplyButton()

        updatePreview()
    }

    // --- Initialization and Setup Helper Methods ---

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fl_settings_main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initWidgetIdAndPrefs() {
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_CANCELED, resultValue)
            finish()
        }
        prefs = applicationContext.getSharedPreferences(WIDGET_PREF_KEY, MODE_PRIVATE)
    }

    private fun initMasterParts() {
        masterPartNames = resources.getStringArray(R.array.part_names).toList()
        masterPartKeys = resources.getStringArray(R.array.part_keys).toList()
        partNames.addAll(masterPartNames)
        partKeys.addAll(masterPartKeys)
    }

    private fun loadWidgetPropsToUI() {
        val wp = getAppWidgetProps(prefs, appWidgetId)
        when (wp.text) {
            null, "" -> v.rbLabelNone.toggle()
            DEFAULT_TEXT -> v.rbLabelRecommended.toggle()
            else -> v.rbLabelCustom.toggle()
        }
        v.etFormat.setText(wp.format)
        textLocale = wp.textLocale ?: ""
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
        v.cbShowSunMoonInside.isChecked = wp.showSunMoonInside
        v.cbDayOfYear.isChecked = 0 < wp.dayOfYear
        v.cbMonthDots.isChecked = 0 < wp.dayOfYearDots
        v.cbMoonPhase.isChecked = wp.moonPhase

        if (wp.timezone == LOCATION_FIXED) {
            v.etLatitude.setText(wp.lat.toString())
            v.etLongitude.setText(wp.lng.toString())
            v.rbLocationFixed.isChecked = true
        } else {
            LatLng.getCoordinatesByTimeZone(applicationContext)?.let { (lat, lng) ->
                v.etLatitude.setText(lat.toString())
                v.etLongitude.setText(lng.toString())
            }
            v.rbLocationTimezone.isChecked = true
        }

        v.rgTrigger.check(
            if (wp.useFgs) R.id.rb_use_fgs else if (wp.useAlarmMethod) R.id.rb_use_alarm_method else R.id.rb_trigger_default
        )
        backgroundAlpha = wp.backgroundAlpha
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

        tapBehavior = wp.tapBehavior ?: TAP_BEHAVIOR_NONE
        v.tvTapBehavior.text = when (tapBehavior) {
            TAP_BEHAVIOR_NONE -> getString(R.string.none)
            TAP_BEHAVIOR_ALARM -> getString(R.string.alarm)
            TAP_BEHAVIOR_CALENDAR -> getString(R.string.calendar)
            else -> wp.tapBehaviorLabel
        }
    }

    // --- Event Listeners and UI Operations ---

    private fun setupPreviewListeners() {
        v.llSettingsMain.setOnClickListener { colorChipAdapter?.select(-1) }
        v.llSettings.setOnClickListener { colorChipAdapter?.select(-1) }

        v.rgLabel.setOnCheckedChangeListener { _, i ->
            updatePreview()
            if (i == R.id.rb_label_custom) {
                v.etFormat.requestFocus()
            } else {
                v.etFormat.clearFocus()
            }
        }
        v.etFormat.addTextChangedListener { v.rbLabelCustom.toggle() }
        v.tvLabelNote.movementMethod = LinkMovementMethod.getInstance()
        v.rgRotate.setOnCheckedChangeListener { _, _ -> updatePreview() }
        v.cbMinute.setOnCheckedChangeListener { _, _ -> updatePreview() }
        v.cbMinuteAndHourDots.setOnCheckedChangeListener { _, _ -> updatePreview() }
        v.cbShowSunMoonInside.setOnCheckedChangeListener { _, _ -> updatePreview() }
        v.cbDayOfYear.setOnCheckedChangeListener { _, _ -> updatePreview() }
        v.cbMonthDots.setOnCheckedChangeListener { _, _ -> updatePreview() }
        v.cbMoonPhase.setOnCheckedChangeListener { _, _ -> updatePreview() }

        v.sbAlpha.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updatePreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        v.tvTextLocale.setOnClickListener { showLanguageDialog() }
    }

    private fun setupColorPalette() {
        arrayOf(v.sbAlpha, v.sbRed, v.sbGreen, v.sbBlue).forEach {
            it.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    updateColor()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }

        colorChipAdapter = PartsAdapter(partNames, partKeys, colors) { selectedKey ->
            onClickColorChip(selectedKey)
        }
        v.rvPartsSelector.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        v.rvPartsSelector.adapter = colorChipAdapter

        val palletBitmap by lazy { v.ivPallet.drawable?.toBitmap() }
        @SuppressLint("ClickableViewAccessibility")
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
        colorChipAdapter?.select(0)
        onClickColorChip(partKeys[0])
    }

    private fun setupLocationSettings() {
        v.rgLocationMode.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rb_location_timezone) {
                v.llLocationFixed.isEnabled = false
                LatLng.getCoordinatesByTimeZone(applicationContext)?.let { (lat, lng) ->
                    v.etLatitude.setText(lat.toString())
                    v.etLongitude.setText(lng.toString())
                    updatePreview()
                }
            } else {
                v.llLocationFixed.isEnabled = true
                updatePreview()
            }
        }

        v.etLatitude.addTextChangedListener { updatePreview() }
        v.etLongitude.addTextChangedListener { updatePreview() }
        v.btnGetCurrentLocation.setOnClickListener {
            checkLocationPermissionAndFetch()
            updatePreview()
        }
    }

    private fun setupTapBehavior() {
        val appPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.component?.let {
                    tapBehavior = it.flattenToString()
                    v.tvTapBehavior.text = getAppLabel(this@SettingsActivity, it.packageName)
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
            AlertDialog.Builder(this).setTitle(R.string.tap_behavior)
                .setItems(tapBehaviors) { _, which ->
                    v.tvTapBehavior.text = tapBehaviors[which]
                    tapBehavior = when (which) {
                        0 -> TAP_BEHAVIOR_NONE
                        1 -> TAP_BEHAVIOR_ALARM
                        2 -> TAP_BEHAVIOR_CALENDAR
                        else -> TAP_BEHAVIOR_APP
                    }
                    if (tapBehavior == TAP_BEHAVIOR_APP) {
                        val intent = Intent(Intent.ACTION_PICK_ACTIVITY).apply {
                            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                                addCategory(Intent.CATEGORY_LAUNCHER)
                            }
                            putExtra(Intent.EXTRA_INTENT, mainIntent)
                            putExtra(Intent.EXTRA_TITLE, getString(R.string.tap_behavior))
                        }
                        appPickerLauncher.launch(intent)
                    }
                }.show()
        }
    }

    @SuppressLint("BatteryLife")
    private fun setupPerformanceSettings() {
        v.cbPreventTimeLag.setOnClickListener {
            if (!canScheduleExact()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
            if (!isIgnoringBatteryOpt()) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:${applicationContext.packageName}".toUri()
                }
                startActivity(intent)
            }
        }
        updatePreventTimeLagState()

        v.rbUseFgs.setOnCheckedChangeListener { _, bool ->
            if (!bool) return@setOnCheckedChangeListener
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@setOnCheckedChangeListener
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) return@setOnCheckedChangeListener
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQ_CODE_NOTIFICATION_PERMISSION
            )
        }
    }

    private fun setupSupportAndLicenses() {
        v.tvLicenses.setOnClickListener {
            startActivity(Intent(this, OssLicenseActivity::class.java))
        }

        updateDonationLabel()
        v.tvSupport.setOnClickListener {
            DonationHelper.start(this) {
                updateDonationLabel()
            }
        }
    }

    private fun setupApplyButton() {
        v.btnApply.setOnClickListener {
            val formatValue = v.etFormat.text.toString()
            val textValue = if (v.rbLabelRecommended.isChecked) DEFAULT_TEXT
            else if (v.rbLabelCustom.isChecked) formatValue
            else ""

            prefs.edit().apply {
                val wp = createAppWidgetProps()
                wp.text = escapeInvalidDateFormat(textValue)
                wp.format = formatValue
                wp.tapBehavior = tapBehavior
                wp.tapBehaviorLabel = v.tvTapBehavior.text.toString()
                wp.useAlarmMethod = v.rbUseAlarmMethod.isChecked
                wp.useFgs = v.rbUseFgs.isChecked
                putAppWidgetProps(this, wp)
                apply()
            }
            LatLng.clearCache()
            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
            updateAppWidget(applicationContext, appWidgetManager, appWidgetId)
            resetUpdateTrigger(applicationContext)
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
            finish()
        }
    }

    // --- Handlers and Utility Functions ---

    private fun showLanguageDialog() {
        val checkedItem = if (textLocale.isEmpty()) {
            0
        } else {
            val index = systemLocales.indexOfFirst { it.language == textLocale }
            if (index != -1) index + 1 else 0
        }
        AlertDialog.Builder(this).setTitle(getString(R.string.text_locale))
            .setSingleChoiceItems(localeNames.toTypedArray(), checkedItem) { dialog, which ->
                textLocale = if (which == 0) "" else systemLocales[which - 1].language
                updatePreview()
                dialog.dismiss()
            }.setNegativeButton(getString(R.string.cancel), null).show()
    }

    private fun setupARGBSeekBars(argb: Int, alpha: Boolean) {
        val key = selectedPart
        selectedPart = ""
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

    private fun updateColor() {
        if (selectedPart.isEmpty()) return
        var alpha = v.sbAlpha.progress
        if (selectedPart.endsWith("Area")) {
            backgroundAlpha = v.sbAlpha.progress.toFloat() / 255
            alpha = 255
        }
        colors[selectedPart] = Color.argb(
            alpha, v.sbRed.progress, v.sbGreen.progress, v.sbBlue.progress
        )
        colorChipAdapter?.notifyItemChanged(partKeys.indexOf(selectedPart))
        updatePreview()
    }

    private fun onClickColorChip(selectedKey: String) {
        selectedPart = selectedKey
        if (selectedPart.isNotEmpty()) {
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

    private fun toggleColorPart(key: String, isVisible: Boolean) {
        val masterIndex = masterPartKeys.indexOf(key)
        if (masterIndex == -1) return
        if (isVisible) {
            if (!partKeys.contains(key)) {
                val insertIndex = masterPartKeys.take(masterIndex).count { partKeys.contains(it) }
                partNames.add(insertIndex, masterPartNames[masterIndex])
                partKeys.add(insertIndex, key)
                colorChipAdapter?.notifyItemInserted(insertIndex)
            }
        } else {
            if (selectedPart == key) {
                selectedPart = ""
                colorChipAdapter?.select(-1)
            }
            val removeIndex = partKeys.indexOf(key)
            if (removeIndex != -1) {
                partNames.removeAt(removeIndex)
                partKeys.removeAt(removeIndex)
                colorChipAdapter?.notifyItemRemoved(removeIndex)
            }
        }
    }

    private fun updatePreview() {
        LatLng.clearCache()
        val views = RemoteViews(applicationContext.packageName, R.layout.app_widget)
        views.setTextViewTextSize(R.id.tv_label, COMPLEX_UNIT_PX, v.etFormat.textSize)
        updateAppWidgetContent(
            applicationContext,
            views,
            createAppWidgetProps(),
            resources.getDimension(R.dimen.preview_size)
        )
        v.preview.removeAllViews()
        v.preview.addView(views.apply(applicationContext, v.preview))
        v.etFormat.isVisible = v.rbLabelCustom.isChecked
        toggleColorPart("colorMinute", v.cbMinute.isChecked)
        toggleColorPart("colorMinuteDots", v.cbMinuteAndHourDots.isChecked)
        toggleColorPart("colorDayOfYear", v.cbDayOfYear.isChecked)
        toggleColorPart("colorDayOfYearDots", v.cbMonthDots.isChecked)
        toggleColorPart("colorText", !v.rbLabelNone.isChecked)
        v.cbShowSunMoonInside.isEnabled =
            v.cbMinuteAndHourDots.isChecked && !v.rbRotateFixHourHand.isChecked
        if (!v.cbMinuteAndHourDots.isChecked) {
            v.cbShowSunMoonInside.isChecked = false
        } else if (v.rbRotateFixHourHand.isChecked) {
            v.cbShowSunMoonInside.isChecked = true
        }
        colors["colorText"]?.let { c -> v.etFormat.setTextColor(c) }
        v.tvTextLocale.text = if (textLocale.isEmpty()) getString(R.string.system_default)
        else Locale.forLanguageTag(textLocale).getDisplayLanguage(Locale.ENGLISH)

        val isFixed = v.rbLocationFixed.isChecked
        v.llLocationFixed.isEnabled = isFixed
        v.llLocationFixed.children.forEach { child ->
            child.isEnabled = isFixed
            child.alpha = if (isFixed) 1.0f else 0.5f
        }
    }

    private fun createAppWidgetProps(): AppWidgetProps {
        val wp = AppWidgetProps(
            id = appWidgetId,
            minute = if (v.cbMinute.isChecked) 1F else 0F,
            minuteAndHourDots = if (v.cbMinuteAndHourDots.isChecked) 1F else 0F,
            dayOfYear = if (v.cbDayOfYear.isChecked) 1F else 0F,
            dayOfYearDots = if (v.cbMonthDots.isChecked) 1F else 0F,
            showSunMoonInside = v.cbShowSunMoonInside.isChecked,
            text = if (v.rbLabelRecommended.isChecked) DEFAULT_TEXT else "",
            textLocale = textLocale,
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
            timezone = if (v.rbLocationTimezone.isChecked) TimeZone.getDefault().id else LOCATION_FIXED,
            lat = v.etLatitude.text.toString().toFloatOrNull() ?: Float.NaN,
            lng = v.etLongitude.text.toString().toFloatOrNull() ?: Float.NaN,
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

    private fun checkLocationPermissionAndFetch() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                fetchAndFillCurrentLocation()
            }

            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchAndFillCurrentLocation() {
        try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                null
            ).addOnSuccessListener { location ->
                if (location != null) {
                    v.etLatitude.setText(String.format(Locale.US, "%.4f", location.latitude))
                    v.etLongitude.setText(String.format(Locale.US, "%.4f", location.longitude))
                } else {
                    android.widget.Toast.makeText(
                        this,
                        getString(R.string.location_not_found),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }.addOnFailureListener { e ->
                e.printStackTrace()
                android.widget.Toast.makeText(
                    this,
                    getString(R.string.location_failed, e.message ?: ""),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            android.widget.Toast.makeText(
                this,
                getString(R.string.location_permission_required),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateDonationLabel() {
        val donationCount = DonationHelper.getDonationCount(this)
        v.tvSupport.text = getString(R.string.donation_star).repeat(donationCount)
    }

    private fun getAppLabel(context: Context, packageName: String?): String {
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
        v.llSettingsMain.orientation =
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

    private fun updatePreventTimeLagState() {
        val enable = canScheduleExact() && isIgnoringBatteryOpt()
        v.cbPreventTimeLag.isChecked = enable
        v.cbPreventTimeLag.isEnabled = !enable
        if (enable) {
            v.cbPreventTimeLag.buttonTintList = getColorStateList(R.color.success)
        }
    }

    private fun canScheduleExact(): Boolean {
        val manager = getSystemService(ALARM_SERVICE) as AlarmManager
        return manager.canScheduleExactAlarms()
    }

    private fun isIgnoringBatteryOpt(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun escapeInvalidDateFormat(input: String): String {
        if (input.isEmpty()) return input
        return buildString {
            var inUserQuotes = false
            var inAutoQuotes = false
            val qt = '\''
            for (char in input) {
                if (char == qt) {
                    if (inAutoQuotes) {
                        append(qt)
                        inAutoQuotes = false
                    }
                    inUserQuotes = !inUserQuotes
                    append(char)
                    continue
                }
                if (inUserQuotes) {
                    append(char)
                    continue
                }
                if (INVALID_DATE_FORMAT_LETTERS.contains(char)) {
                    if (!inAutoQuotes) {
                        append(qt)
                        inAutoQuotes = true
                    }
                } else if (inAutoQuotes) {
                    append(qt)
                    inAutoQuotes = false
                }
                append(char)
            }
            if (inAutoQuotes) {
                append('\'')
            }
        }
    }
}