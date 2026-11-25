package utb.dip.jp.simple24hclock

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

data class SelectableOption<out T>(
    val value: T,
    val title: CharSequence,
    val searchText: String = title.toString(),
)

fun <T> showCommonPickerDialog(
    context: Context,
    titleResId: Int,
    options: List<SelectableOption<T>>,
    selectedValue: T?,
    enableSearch: Boolean = false,
    onSelected: (T) -> Unit,
) {
    var filteredOptions = options

    val density = context.resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    val accentColor = TypedValue().let { tv ->
        context.theme.resolveAttribute(android.R.attr.colorAccent, tv, true)
        if (tv.resourceId != 0) context.getColor(tv.resourceId) else tv.data
    }
    val selectedBgColor = ColorUtils.setAlphaComponent(accentColor, 36)
    val defaultTextColor = TextView(context).currentTextColor

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(12), dp(20), 0)
    }

    val searchInput = if (enableSearch) {
        EditText(context).apply {
            hint = context.getString(android.R.string.search_go)
            isSingleLine = true
        }
    } else {
        null
    }

    val listHeightPx = if (enableSearch) dp(360) else ViewGroup.LayoutParams.WRAP_CONTENT
    val rowHeightPx = dp(48)

    val listView = ListView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            listHeightPx
        )
    }

    val adapter = object : ArrayAdapter<SelectableOption<T>>(
        context,
        android.R.layout.simple_list_item_1,
        filteredOptions.toMutableList()
    ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent) as TextView
            val item = getItem(position)
            val isSelected = item != null && item.value == selectedValue

            view.text = item?.title ?: ""
            if (isSelected) {
                view.setTextColor(accentColor)
                view.setTypeface(null, Typeface.BOLD)
                view.setBackgroundColor(selectedBgColor)
            } else {
                view.setTextColor(defaultTextColor)
                view.setTypeface(null, Typeface.NORMAL)
                view.setBackgroundColor(Color.TRANSPARENT)
            }
            return view
        }
    }
    listView.adapter = adapter

    // Push remaining items toward the bottom when filtered results are short so they stay reachable
    fun syncBottomAlignmentPadding() {
        if (!enableSearch || listHeightPx <= 0) {
            listView.setPadding(0, 0, 0, 0)
            return
        }
        val estimatedContentHeight = filteredOptions.size * rowHeightPx
        val topSpacer = (listHeightPx - estimatedContentHeight).coerceAtLeast(0)
        listView.setPadding(0, topSpacer, 0, 0)
        listView.clipToPadding = false
    }

    fun refreshList(scrollToSelected: Boolean) {
        syncBottomAlignmentPadding()
        adapter.notifyDataSetChanged()
        if (scrollToSelected) {
            val index = filteredOptions.indexOfFirst { it.value == selectedValue }
            if (index >= 0) {
                listView.setSelection(index)
            }
        }
    }

    val dialog = AlertDialog.Builder(context)
        .setTitle(context.getString(titleResId))
        .setView(container)
        .setNegativeButton(context.getString(R.string.cancel), null)
        .create()

    listView.setOnItemClickListener { _, _, position, _ ->
        val clicked = filteredOptions.getOrNull(position) ?: return@setOnItemClickListener
        onSelected(clicked.value)
        dialog.dismiss()
    }

    searchInput?.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            val q = s?.toString()?.trim().orEmpty()
            filteredOptions = if (q.isEmpty()) {
                options
            } else {
                options.filter { opt ->
                    opt.searchText.contains(q, ignoreCase = true)
                }
            }
            adapter.clear()
            adapter.addAll(filteredOptions)
            refreshList(scrollToSelected = false)
        }

        override fun afterTextChanged(s: Editable?) = Unit
    })

    container.addView(listView)
    searchInput?.let { input ->
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(8)
        }
        container.addView(input, lp)
    }

    refreshList(scrollToSelected = true)
    dialog.show()
}

private data class TimeZoneSortEntry(
    val option: SelectableOption<String?>,
    val offsetSeconds: Int,
)

private fun formatGmtOffset(offset: ZoneOffset): String {
    val totalMinutes = offset.totalSeconds / 60
    val sign = if (totalMinutes >= 0) "+" else "-"
    val absMinutes = abs(totalMinutes)
    val hours = absMinutes / 60
    val minutes = absMinutes % 60
    return String.format(Locale.US, "GMT%s%02d:%02d", sign, hours, minutes)
}

private fun buildStyledZoneTitle(
    primary: String,
    gmtOffset: String,
    subTextColor: Int,
    belowLine: Boolean = false,
): SpannableString {
    val separator = if (belowLine) "\n" else " "
    val suffix = "$separator($gmtOffset)"
    val full = "$primary$suffix"
    val start = primary.length
    val end = full.length

    return SpannableString(full).apply {
        // Make GMT offset smaller and slightly muted so long zone names stay readable
        setSpan(RelativeSizeSpan(0.8f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(ForegroundColorSpan(subTextColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

fun showTimeZonePickerDialog(
    context: Context,
    currentZoneId: String?,
    onSelected: (String?) -> Unit,
) {
    val now: Instant = TimeUtils.rawInstant()
    val locale: Locale = Locale.getDefault()
    val systemDefaultLabel = context.getString(R.string.system_default)
    val defaultOffset = ZoneId.systemDefault().rules.getOffset(now)

    val subTextColor = ColorUtils.setAlphaComponent(TextView(context).currentTextColor, 160)
    val normalizedCurrent = currentZoneId?.trim()?.takeIf { it.isNotEmpty() }

    // Restrict selectable time zones to IDs present in timezones.json (derived from zone1970.tab)
    val supportedZoneIds: Set<String> = LatLng.getAllTimeZoneCoordinates(context).keys

    val sortedEntries = ZoneId.getAvailableZoneIds()
        .asSequence()
        // Exclude legacy 3-letter short IDs such as "JST", "EST", "PST"
        .filter { id -> id !in ZoneId.SHORT_IDS && id in supportedZoneIds }
        .mapNotNull { zoneIdStr ->
            runCatching {
                val zoneId = ZoneId.of(zoneIdStr)
                val offset = zoneId.rules.getOffset(now)
                val displayName = zoneId.getDisplayName(TextStyle.FULL, locale)
                val gmt = formatGmtOffset(offset)
                TimeZoneSortEntry(
                    option = SelectableOption(
                        value = zoneIdStr,
                        title = buildStyledZoneTitle(displayName, gmt, subTextColor),
                        searchText = "$displayName $gmt $zoneIdStr",
                    ),
                    offsetSeconds = offset.totalSeconds,
                )
            }.getOrNull()
        }
        .toList()
        // Prefer canonical "Area/Location" IDs (containing '/') over top-level aliases
        .sortedByDescending { entry -> entry.option.value?.contains('/') == true }
        .distinctBy { it.option.title.toString() }
        .sortedWith(
            compareBy<TimeZoneSortEntry> { it.offsetSeconds }
                .thenBy { it.option.title.toString() }
                .thenBy { it.option.value ?: "" }
        )

    val allOptions = buildList {
        val defaultGmt = formatGmtOffset(defaultOffset)
        add(
            SelectableOption(
                value = null,
                title = buildStyledZoneTitle(systemDefaultLabel, defaultGmt, subTextColor),
                searchText = "$systemDefaultLabel $defaultGmt",
            )
        )
        addAll(sortedEntries.map { it.option })
    }

    showCommonPickerDialog(
        context = context,
        titleResId = R.string.custom_time_zone,
        options = allOptions,
        selectedValue = normalizedCurrent,
        enableSearch = true,
        onSelected = onSelected,
    )
}