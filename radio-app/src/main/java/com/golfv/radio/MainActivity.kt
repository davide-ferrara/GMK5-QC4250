package com.golfv.radio

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

private enum class RadioAccentTheme(
    val preferenceValue: String,
    val primary: Int,
    val primaryPressed: Int,
    val accent: Int,
    val surfacePressed: Int,
    val border: Int,
    val scaleNeedle: Int,
) {
    VolkswagenBlue(
        "volkswagen-blue",
        Color.rgb(35, 109, 168),
        Color.rgb(52, 126, 189),
        Color.rgb(143, 211, 255),
        Color.rgb(43, 74, 98),
        Color.rgb(43, 58, 73),
        Color.rgb(179, 38, 50),
    ),
    InstrumentRed(
        "instrument-red",
        Color.rgb(179, 38, 50),
        Color.rgb(211, 59, 71),
        Color.rgb(255, 133, 140),
        Color.rgb(78, 36, 42),
        Color.rgb(76, 48, 55),
        Color.rgb(255, 133, 140),
    );

    companion object {
        fun fromPreference(value: String?): RadioAccentTheme? =
            entries.firstOrNull { it.preferenceValue == value }
    }
}

class MainActivity : Activity() {
    private lateinit var frequencyView: TextView
    private lateinit var stationNameView: TextView
    private lateinit var tuningScale: TuningScaleView
    private val presetViews = mutableListOf<TextView>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val logExecutor = Executors.newSingleThreadExecutor()
    private val preferences: SharedPreferences by lazy {
        getSharedPreferences(PREFS, MODE_PRIVATE)
    }
    private var logcatProcess: Process? = null
    private var receiverRegistered = false
    private var radioStarted = false
    private var commandGeneration = 0
    private var pendingTuneFrequency: Int? = null
    private var currentFrequency = -1
    private var currentStationName = ""
    private var accentTheme = RadioAccentTheme.VolkswagenBlue
    private var themeReceiverRegistered = false

    private val frequencyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_FREQUENCY_CHANGED) return
            val frequency = intent.getIntExtra(EXTRA_FREQUENCY, -1)
            if (frequency > 0) updateFrequency(frequency)
        }
    }

    private val accentThemeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_ACCENT_THEME_CHANGED) return
            updateAccentTheme(intent.getStringExtra(EXTRA_ACCENT_THEME))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accentTheme = RadioAccentTheme.fromPreference(
            intent?.getStringExtra(EXTRA_ACCENT_THEME)
                ?: preferences.getString(KEY_ACCENT_THEME, null),
        ) ?: RadioAccentTheme.VolkswagenBlue
        preferences.edit().putString(KEY_ACCENT_THEME, accentTheme.preferenceValue).apply()
        currentFrequency = preferences.getInt(KEY_LAST_FREQUENCY, -1)
        currentStationName = preferences.getString(KEY_LAST_STATION_NAME, null)
            ?: getString(R.string.rds_waiting)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContentView(createContentView())
        showGolfWelcome()
    }

    override fun onStart() {
        super.onStart()
        radioStarted = true
        if (!receiverRegistered) {
            registerReceiver(frequencyReceiver, IntentFilter(ACTION_FREQUENCY_CHANGED))
            receiverRegistered = true
        }
        if (!themeReceiverRegistered) {
            registerReceiver(
                accentThemeReceiver,
                IntentFilter(ACTION_ACCENT_THEME_CHANGED),
                THEME_BROADCAST_PERMISSION,
                null,
            )
            themeReceiverRegistered = true
        }
        restoreRadio()
        startRdsReader()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_ACCENT_THEME)?.let(::updateAccentTheme)
    }

    override fun onStop() {
        radioStarted = false
        commandGeneration++
        pendingTuneFrequency = null
        persistCurrentStation()
        logcatProcess?.destroy()
        logcatProcess = null
        if (receiverRegistered) {
            unregisterReceiver(frequencyReceiver)
            receiverRegistered = false
        }
        if (themeReceiverRegistered) {
            unregisterReceiver(accentThemeReceiver)
            themeReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        logExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun createContentView(): View {
        presetViews.clear()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = panelBackground()
        }
        root.addView(createPresetColumn(0), sideColumnParams())
        root.addView(createCenterPanel(), LinearLayout.LayoutParams(0, MATCH, 1f).apply {
            marginStart = dp(12)
            marginEnd = dp(12)
        })
        root.addView(createPresetColumn(4), sideColumnParams())
        refreshPresetViews()
        return root
    }

    private fun updateAccentTheme(preferenceValue: String?) {
        val selectedTheme = RadioAccentTheme.fromPreference(preferenceValue) ?: return
        if (accentTheme == selectedTheme) return

        accentTheme = selectedTheme
        preferences.edit().putString(KEY_ACCENT_THEME, selectedTheme.preferenceValue).apply()
        if (!::frequencyView.isInitialized) return

        val previousStationName = stationNameView.text
        val previousFrequency = frequencyView.text
        val previousScaleFrequency = tuningScale.frequency
        val wasWelcomeVisible = tuningScale.welcome
        setContentView(createContentView())
        stationNameView.text = previousStationName
        frequencyView.text = previousFrequency
        tuningScale.frequency = previousScaleFrequency
        tuningScale.welcome = wasWelcomeVisible
        highlightCurrentPreset()
    }

    private fun createCenterPanel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(16), dp(8), dp(16), dp(8))
        background = displayBackground()

        addView(TextView(this@MainActivity).apply {
            text = getString(R.string.band_label)
            setTextColor(accentTheme.primary)
            textSize = 22f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.15f
        }, LinearLayout.LayoutParams(MATCH, dp(38)))

        stationNameView = TextView(this@MainActivity).apply {
            text = currentStationName
            setTextColor(accentTheme.accent)
            textSize = 38f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            isSingleLine = true
            letterSpacing = 0.08f
        }
        addView(stationNameView, LinearLayout.LayoutParams(MATCH, dp(66)))

        frequencyView = TextView(this@MainActivity).apply {
            text = "--.-"
            setTextColor(ON_SURFACE)
            textSize = 112f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            isSingleLine = true
        }
        addView(frequencyView, LinearLayout.LayoutParams(MATCH, 0, 1f))

        addView(TextView(this@MainActivity).apply {
            text = "MHz"
            setTextColor(accentTheme.primary)
            textSize = 30f
            gravity = Gravity.CENTER
            letterSpacing = 0.22f
        }, LinearLayout.LayoutParams(MATCH, dp(42)))

        tuningScale = TuningScaleView(
            this@MainActivity,
            accentTheme.primary,
            accentTheme.scaleNeedle,
            accentTheme.accent,
        )
        addView(tuningScale, LinearLayout.LayoutParams(MATCH, dp(82)))

        val seekRow = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        seekRow.addView(
            golfButton(getString(R.string.seek_previous)) { sendRadioAction(ACTION_SEEK_PREVIOUS) },
            LinearLayout.LayoutParams(0, dp(72), 1f),
        )
        seekRow.addView(View(this@MainActivity), LinearLayout.LayoutParams(dp(12), 1))
        seekRow.addView(
            golfButton(getString(R.string.seek_next)) { sendRadioAction(ACTION_SEEK_NEXT) },
            LinearLayout.LayoutParams(0, dp(72), 1f),
        )
        addView(seekRow, LinearLayout.LayoutParams(MATCH, dp(74)).apply {
            topMargin = dp(12)
        })

    }

    private fun createPresetColumn(firstIndex: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        repeat(4) { offset ->
            val index = firstIndex + offset
            val preset = TextView(this@MainActivity).apply {
                gravity = Gravity.CENTER
                setTextColor(accentTheme.accent)
                textSize = 23f
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                background = presetBackground(false)
                setPadding(dp(8), dp(6), dp(8), dp(6))
                setOnClickListener { recallPreset(index) }
                setOnLongClickListener {
                    savePreset(index)
                    true
                }
            }
            presetViews += preset
            addView(preset, LinearLayout.LayoutParams(MATCH, 0, 1f).apply {
                topMargin = dp(6)
                bottomMargin = dp(6)
            })
        }
    }

    private fun golfButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 25f
        setTextColor(accentTheme.accent)
        typeface = Typeface.DEFAULT_BOLD
        background = presetBackground(false)
        setOnClickListener { action() }
    }

    private fun updateFrequency(frequency: Int) {
        if (pendingTuneFrequency == frequency) {
            pendingTuneFrequency = null
            commandGeneration++
        }
        val stationChanged = currentFrequency != frequency
        currentFrequency = frequency
        if (stationChanged) currentStationName = getString(R.string.rds_waiting)
        val editor = preferences.edit().putInt(KEY_LAST_FREQUENCY, frequency)
        if (stationChanged) editor.remove(KEY_LAST_STATION_NAME)
        editor.commit()
        frequencyView.text = formatFrequencyNumber(frequency)
        stationNameView.text = currentStationName
        tuningScale.frequency = frequency
        setStatus(getString(R.string.tuner_connected))
        highlightCurrentPreset()
    }

    private fun updateRds(frequency: Int, name: String) {
        if (name.isBlank() || (currentFrequency > 0 && frequency != currentFrequency)) return
        if (currentFrequency <= 0) updateFrequency(frequency)
        currentStationName = name.trim().uppercase(Locale.ITALY)
        preferences.edit()
            .putInt(KEY_LAST_FREQUENCY, frequency)
            .putString(KEY_LAST_STATION_NAME, currentStationName)
            .commit()
        stationNameView.text = currentStationName
        updateSavedName(frequency, currentStationName)
        refreshPresetViews()
        setStatus(getString(R.string.rds_station, currentStationName))
    }

    private fun sendRadioAction(action: String) {
        commandGeneration++
        pendingTuneFrequency = null
        if (sendServiceIntent(Intent(action).apply { component = RADIO_SERVICE })) {
            setStatus(getString(R.string.seeking_station))
        }
    }

    private fun tuneTo(frequency: Int) {
        if (frequency <= 0) return
        if (currentFrequency != frequency) {
            currentStationName = getString(R.string.rds_waiting)
            stationNameView.text = currentStationName
        }
        currentFrequency = frequency
        frequencyView.text = formatFrequencyNumber(frequency)
        tuningScale.frequency = frequency
        highlightCurrentPreset()
        persistCurrentStation()
        sendTuneWithRetry(frequency)
        setStatus(getString(R.string.tuning_to, formatFrequency(frequency)))
    }

    private fun restoreRadio() {
        startRadioBackend()
        val savedFrequency = currentFrequency.takeIf { it > 0 }
            ?: firstSavedPreset()
        if (savedFrequency != null) {
            currentFrequency = savedFrequency
            frequencyView.text = formatFrequencyNumber(savedFrequency)
            tuningScale.frequency = savedFrequency
            mainHandler.postDelayed({
                if (radioStarted) sendTuneWithRetry(savedFrequency)
            }, BACKEND_WARMUP_MS)
        } else {
            // A seek is the only command verified on the QC4250 to open the
            // tuner, request audio focus and publish the current frequency.
            mainHandler.postDelayed({
                if (radioStarted && currentFrequency <= 0) startRadioBackend()
            }, BACKEND_WARMUP_MS)
            mainHandler.postDelayed({
                if (radioStarted && currentFrequency <= 0) {
                    sendRadioAction(ACTION_SEEK_NEXT)
                }
            }, FIRST_TUNE_DELAY_MS)
        }
    }

    private fun sendTuneWithRetry(frequency: Int) {
        val generation = ++commandGeneration
        pendingTuneFrequency = frequency
        TUNE_RETRY_DELAYS_MS.forEach { delay ->
            mainHandler.postDelayed({
                if (!radioStarted || generation != commandGeneration || pendingTuneFrequency != frequency) {
                    return@postDelayed
                }
                val sent = sendServiceIntent(Intent(ACTION_CONTROL_RADIO).apply {
                    component = RADIO_SERVICE
                    putExtra(EXTRA_METHOD, METHOD_SET_FREQUENCY)
                    putExtra(EXTRA_PARAMETER_FREQUENCY, frequency)
                })
                if (!sent) setStatus(getString(R.string.tuning_failed))
            }, delay)
        }
    }

    /** GalaRadio remains the owner of the tuner and the radio audio source. */
    private fun startRadioBackend(): Boolean =
        sendServiceIntent(Intent().apply { component = RADIO_SERVICE })

    private fun sendServiceIntent(intent: Intent): Boolean = try {
            startForegroundService(intent)
            if (currentFrequency <= 0) {
                setStatus(getString(R.string.starting_tuner))
            }
            true
        } catch (error: Exception) {
            Log.e("GolfRadio", "OEM radio command failed", error)
            setStatus(getString(R.string.oem_service_unavailable))
            false
        }

    private fun savePreset(index: Int) {
        if (currentFrequency <= 0) {
            Toast.makeText(this, R.string.frequency_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val saved = preferences.edit()
            .putInt("frequency_$index", currentFrequency)
            .putString("name_$index", currentStationName.takeUnless { it == getString(R.string.rds_waiting) })
            .putInt(KEY_LAST_FREQUENCY, currentFrequency)
            .commit()
        if (!saved) {
            Toast.makeText(this, R.string.preset_save_failed, Toast.LENGTH_SHORT).show()
            return
        }
        refreshPresetViews()
        Toast.makeText(this, getString(R.string.preset_saved, index + 1), Toast.LENGTH_SHORT).show()
    }

    private fun recallPreset(index: Int) {
        val frequency = preferences.getInt("frequency_$index", -1)
        if (frequency <= 0) {
            Toast.makeText(this, R.string.save_preset_here, Toast.LENGTH_SHORT).show()
            return
        }
        tuneTo(frequency)
    }

    private fun refreshPresetViews() {
        presetViews.forEachIndexed { index, view ->
            val frequency = preferences.getInt("frequency_$index", -1)
            val name = preferences.getString("name_$index", null)
            view.text = if (frequency > 0) {
                "${index + 1}  ${name?.take(9) ?: "FM"}\n${formatFrequencyNumber(frequency)}"
            } else {
                "${index + 1}\n— — —"
            }
        }
        highlightCurrentPreset()
    }

    private fun highlightCurrentPreset() {
        presetViews.forEachIndexed { index, view ->
            view.background = presetBackground(
                currentFrequency > 0 && preferences.getInt("frequency_$index", -1) == currentFrequency,
            )
        }
    }

    private fun updateSavedName(frequency: Int, name: String) {
        val editor = preferences.edit()
        var changed = false
        repeat(8) { index ->
            if (preferences.getInt("frequency_$index", -1) == frequency) {
                editor.putString("name_$index", name)
                changed = true
            }
        }
        if (changed) editor.commit()
    }

    private fun firstSavedPreset(): Int? = (0 until PRESET_COUNT)
        .firstNotNullOfOrNull { index ->
            preferences.getInt("frequency_$index", -1).takeIf { it > 0 }
        }

    private fun persistCurrentStation() {
        if (currentFrequency <= 0) return
        preferences.edit()
            .putInt(KEY_LAST_FREQUENCY, currentFrequency)
            .putString(
                KEY_LAST_STATION_NAME,
                currentStationName.takeUnless { it == getString(R.string.rds_waiting) },
            )
            .commit()
    }

    private fun startRdsReader() {
        if (checkSelfPermission(android.Manifest.permission.READ_LOGS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            setStatus(getString(R.string.rds_permission_required))
            return
        }
        if (logcatProcess != null) return

        logExecutor.execute {
            try {
                val process = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-T", "1", "-v", "raw", "-s", "McuRadio:D", "*:S"),
                )
                logcatProcess = process
                BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                    lines.forEach { parseRdsLine(it) }
                }
            } catch (_: Exception) {
                mainHandler.post { setStatus(getString(R.string.rds_unavailable)) }
            } finally {
                logcatProcess = null
            }
        }
    }

    private fun parseRdsLine(line: String) {
        if (!line.contains("\"Action\":\"RDS\"")) return
        val frequency = RDS_FREQUENCY.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return
        val values = RDS_PS.find(line)?.groupValues?.get(1)
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?: return
        val length = values.firstOrNull()?.coerceIn(0, values.size - 1) ?: return
        val name = values.drop(1).take(length).map { it.toChar() }.joinToString("").trim()
        if (name.isNotBlank()) mainHandler.post { updateRds(frequency, name) }
    }

    private fun showGolfWelcome() {
        stationNameView.text = getString(R.string.welcome)
        frequencyView.text = getString(R.string.welcome_frequency)
        tuningScale.welcome = true
        mainHandler.postDelayed({
            stationNameView.text = currentStationName
            frequencyView.text = if (currentFrequency > 0) formatFrequencyNumber(currentFrequency) else "--.-"
            tuningScale.welcome = false
        }, 1_250)
    }

    private fun setStatus(message: String) {
        Log.d("GolfRadio", message)
    }

    private fun formatFrequencyNumber(raw: Int): String = when {
        raw >= 10_000 -> String.format(Locale.ITALY, "%.1f", raw / 1000.0)
        raw >= 1_000 -> String.format(Locale.ITALY, "%.1f", raw / 100.0)
        else -> raw.toString()
    }

    private fun formatFrequency(raw: Int) = "${formatFrequencyNumber(raw)} MHz"

    private fun panelBackground() = GradientDrawable().apply {
        setColor(BACKGROUND)
        cornerRadius = dp(8).toFloat()
        setStroke(dp(1), accentTheme.border)
    }

    private fun displayBackground() = GradientDrawable().apply {
        setColor(SURFACE)
        cornerRadius = dp(8).toFloat()
        setStroke(dp(1), accentTheme.primary)
    }

    /** A high-contrast pressed state makes controls usable at a glance in the car. */
    private fun presetBackground(selected: Boolean) = StateListDrawable().apply {
        addState(
            intArrayOf(android.R.attr.state_pressed),
            solidButtonBackground(
                if (selected) accentTheme.primaryPressed else accentTheme.surfacePressed,
                accentTheme.accent,
                2,
            ),
        )
        addState(
            intArrayOf(),
            solidButtonBackground(
                if (selected) accentTheme.primary else SURFACE_ELEVATED,
                if (selected) accentTheme.accent else accentTheme.border,
            ),
        )
    }

    private fun solidButtonBackground(fill: Int, border: Int, borderWidth: Int = 1) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(5).toFloat()
        setStroke(dp(borderWidth), border)
    }

    private fun sideColumnParams() = LinearLayout.LayoutParams(dp(224), MATCH)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val PREFS = "radio_presets"
        const val KEY_ACCENT_THEME = "accent_theme"
        const val KEY_LAST_FREQUENCY = "last_frequency"
        const val KEY_LAST_STATION_NAME = "last_station_name"
        const val PRESET_COUNT = 8
        const val RADIO_PACKAGE = "com.acloud.stub.extradio"
        val RADIO_SERVICE = ComponentName(RADIO_PACKAGE, "com.radio.service.RadioService")

        const val ACTION_SEEK_NEXT = "xy.android.fmradio.frd"
        const val ACTION_SEEK_PREVIOUS = "xy.android.fmradio.rev"
        const val ACTION_FREQUENCY_CHANGED = "xy.update.freq"
        const val ACTION_CONTROL_RADIO = "com.android.xygala.ACTION_CONTROL_RADIO"
        const val EXTRA_FREQUENCY = "freq"
        const val EXTRA_METHOD = "method"
        const val EXTRA_PARAMETER_FREQUENCY = "param_freq"
        const val METHOD_SET_FREQUENCY = "method_setFreq"
        const val ACTION_ACCENT_THEME_CHANGED = "com.golfv.launcher.action.ACCENT_THEME_CHANGED"
        const val EXTRA_ACCENT_THEME = "com.golfv.launcher.extra.ACCENT_THEME"
        const val THEME_BROADCAST_PERMISSION = "com.golfv.radio.permission.UPDATE_THEME"
        const val BACKEND_WARMUP_MS = 300L
        const val FIRST_TUNE_DELAY_MS = 900L
        val TUNE_RETRY_DELAYS_MS = longArrayOf(0L, 900L, 2_400L)

        val BACKGROUND = Color.rgb(9, 12, 16)
        val SURFACE = Color.rgb(17, 24, 32)
        val SURFACE_ELEVATED = Color.rgb(24, 33, 43)
        val ON_SURFACE = Color.rgb(232, 236, 242)
        val RDS_FREQUENCY = Regex("\"Freq\":(\\d+)")
        val RDS_PS = Regex("\"PS\":\\[([^]]+)]")
    }
}

private class TuningScaleView(
    context: Context,
    scaleColor: Int,
    needleColor: Int,
    labelColor: Int,
) : View(context) {
    var frequency: Int = -1
        set(value) {
            field = value
            invalidate()
        }
    var welcome: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = scaleColor
        strokeWidth = resources.displayMetrics.density
    }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = needleColor
        strokeWidth = 3f * resources.displayMetrics.density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor
        textSize = 18f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val padding = 24f * resources.displayMetrics.density
        val usable = width - padding * 2
        val baseY = height * 0.58f
        canvas.drawLine(padding, baseY, width - padding, baseY, linePaint)

        val labels = intArrayOf(88, 92, 96, 100, 104, 108)
        labels.forEachIndexed { index, label ->
            val x = padding + usable * index / (labels.size - 1)
            canvas.drawLine(x, baseY - height * 0.18f, x, baseY + height * 0.10f, linePaint)
            canvas.drawText(label.toString(), x, height * 0.94f, textPaint)
        }

        val mhz = frequency / 1000f
        if (frequency > 0 && mhz in 87.5f..108f) {
            val x = padding + usable * ((mhz - 87.5f) / 20.5f)
            canvas.drawLine(x, 0f, x, baseY + height * 0.15f, needlePaint)
        } else if (welcome) {
            canvas.drawLine(width / 2f, 0f, width / 2f, baseY + height * 0.15f, needlePaint)
        }
    }
}
