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
    private lateinit var delayCheck: CheckBox
    private lateinit var delayLabel: TextView
    private lateinit var delayBar: SeekBar
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

        // --- Threshold ---
        thresholdLabel = TextView(this).apply {
            text = "Threshold: ${prefs.getInt("threshold_db", 65)} dB"
            textSize = 16f
            setPadding(0, 16, 0, 8)
        }
        thresholdBar = SeekBar(this).apply {
            max = 60
            progress = prefs.getInt("threshold_db", 65) - 40
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                    val v = p + 40
                    thresholdLabel.text = "Threshold: $v dB"
                    prefs.edit().putInt("threshold_db", v).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }

        // --- Delay start ---
        delayCheck = CheckBox(this).apply {
            text = "Delay monitoring start"
            isChecked = prefs.getBoolean("delay_enabled", false)
            setPadding(0, 24, 0, 8)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("delay_enabled", checked).apply()
                delayBar.isEnabled = checked
            }
        }

        delayLabel = TextView(this).apply {
            text = "Delay: ${prefs.getInt("delay_minutes", 60)} minutes"
            textSize = 14f
            setPadding(0, 8, 0, 4)
        }

        delayBar = SeekBar(this).apply {
            max = 180
            progress = prefs.getInt("delay_minutes", 60)
            isEnabled = prefs.getBoolean("delay_enabled", false)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                    delayLabel.text = "Delay: $p minutes"
                    prefs.edit().putInt("delay_minutes", p).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }

        // --- Output mode ---
        val outputLabel = TextView(this).apply {
            text = "Output mode:"
            textSize = 16f
            setPadding(0, 24, 0, 8)
        }
        radioSound = RadioButton(this).apply { text = "Sound only" }
        radioVibrate = RadioButton(this).apply { text = "Vibration only" }
        radioBoth = RadioButton(this).apply { text = "Both (default)" }
        when (prefs.getString("output_mode", "both")) {
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

        // --- Tone ---
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

        // --- Buttons ---
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
                    Toast.makeText(this@MainActivity, "Open Settings manually", Toast.LENGTH_LONG).show()
                }
            }
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(thresholdLabel)
        layout.addView(thresholdBar)
        layout.addView(delayCheck)
        layout.addView(delayLabel)
        layout.addView(delayBar)
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

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val threshold = prefs.getInt("threshold_db", 65)
        val delayEnabled = prefs.getBoolean("delay_enabled", false)
        val delayMin = prefs.getInt("delay_minutes", 60)

        statusText.text = if (delayEnabled && delayMin > 0) {
            "Status: WAITING ${delayMin}min, then $threshold dB"
        } else {
            "Status: LISTENING ($threshold dB)"
        }
        Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show()
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
