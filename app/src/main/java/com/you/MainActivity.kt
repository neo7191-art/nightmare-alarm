package com.you.nightmarealarm

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var thresholdLabel: TextView
    private lateinit var thresholdBar: SeekBar
    private lateinit var radioSound: RadioButton
    private lateinit var radioVibrate: RadioButton
    private lateinit var radioBoth: RadioButton
    private lateinit var toneLabel: TextView

    private val PREFS = "nightmare_prefs"
    private val REQ_TONE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }

        val title = TextView(this).apply {
            text = "Nightmare Alarm"
            textSize = 24f
        }

        statusText = TextView(this).apply {
            text = "Status: stopped"
            textSize = 16f
            setPadding(0, 16, 0, 24)
        }

        // --- Threshold section ---
        thresholdLabel = TextView(this).apply {
            text = "Threshold: ${prefs.getInt("threshold_db", 65)} dB"
            textSize = 16f
            setPadding(0, 16, 0, 8)
        }

        thresholdBar = SeekBar(this).apply {
            max = 60                 // 40..100 dB
            progress = prefs.getInt("threshold_db", 65) - 40
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + 40
                    thresholdLabel.text = "Threshold: $value dB"
                    prefs.edit().putInt("threshold_db", value).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        val thresholdHint = TextView(this).apply {
            text = "(Lower = more sensitive. Recommended: 65–80)"
            textSize = 12f
            setPadding(0, 0, 0, 24)
        }

        // --- Output mode section ---
        val outputLabel = TextView(this).apply {
            text = "Output mode:"
            textSize = 16f
            setPadding(0, 16, 0, 8)
        }

        radioSound = RadioButton(this).apply { text = "Sound only" }
        radioVibrate = RadioButton(this).apply { text = "Vibration only" }
        radioBoth = RadioButton(this).apply { text = "Both (default)" }

        val savedMode = prefs.getString("output_mode", "both")
        when (savedMode) {
            "sound" -> radioSound.isChecked = true
            "vibrate" -> radioVibrate.isChecked = true
            else -> radioBoth.isChecked = true
        }

        val modeListener = android.widget.CompoundButton.OnCheckedChangeListener { _, _ ->
            val mode = when {
                radioSound.isChecked -> "sound"
                radioVibrate.isChecked -> "vibrate"
                else -> "both"
            }
            prefs.edit().putString("output_mode", mode).apply()
        }
        radioSound.setOnCheckedChangeListener(modeListener)
        radioVibrate.setOnCheckedChangeListener(modeListener)
        radioBoth.setOnCheckedChangeListener(modeListener)

        // --- Tone section ---
        toneLabel = TextView(this).apply {
            text = "Alarm tone: ${prefs.getString("tone_name", "Default alarm")}"
            textSize = 14f
            setPadding(0, 24, 0, 8)
        }

        val toneBtn = Button(this).apply {
            text = "Change alarm tone"
            setOnClickListener {
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Pick alarm tone")
                    val current = prefs.getString("tone_uri", null)
                    if (current != null) {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(current))
                    }
                }
                startActivityForResult(intent, REQ_TONE)
            }
        }

        // --- Action buttons ---
        val startBtn = Button(this).apply {
            text = "START listening"
            setOnClickListener { startAlarmService() }
        }

        val stopBtn = Button(this).apply {
            text = "STOP listening"
            setOnClickListener {
                stopService(Intent(this@MainActivity, AlarmService::class.java))
                statusText.text = "Status: stopped"
            }
        }

        val batteryBtn = Button(this).apply {
            text = "Disable battery optimization"
            setOnClickListener { requestBatteryExemption() }
        }

        val autostartBtn = Button(this).apply {
            text = "Open Autostart settings (MIUI)"
            setOnClickListener {
                try {
                    val i = Intent().apply {
                        setClassName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    }
                    startActivity(i)
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Open Settings manually",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(thresholdLabel)
        layout.addView(thresholdBar)
        layout.addView(thresholdHint)
        layout.addView(outputLabel)
        layout.addView(radioBoth)
        layout.addView(radioSound)
        layout.addView(radioVibrate)
        layout.addView(toneLabel)
        layout.addView(toneBtn)
        layout.addView(startBtn)
        layout.addView(stopBtn)
        layout.addView(batteryBtn)
        layout.addView(autostartBtn)

        val scroll = ScrollView(this).apply { addView(layout) }
        setContentView(scroll)

        requestRuntimePermissions()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_TONE && resultCode == Activity.RESULT_OK) {
            val uri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                val name = RingtoneManager.getRingtone(this, uri).getTitle(this) ?: "Custom"
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString("tone_uri", uri.toString())
                    .putString("tone_name", name)
                    .apply()
                toneLabel.text = "Alarm tone: $name"
            }
        }
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        }
    }

    private fun startAlarmService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_LONG).show()
            requestRuntimePermissions()
            return
        }
        val intent = Intent(this, AlarmService::class.java)
        ContextCompat.startForegroundService(this, intent)
        val threshold = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("threshold_db", 65)
        statusText.text = "Status: LISTENING ($threshold dB)"
        Toast.makeText(this, "Alarm service started", Toast.LENGTH_SHORT).show()
    }

    private fun requestBatteryExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Already exempted", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
