package com.you.nightmarealarm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        val title = TextView(this).apply {
            text = "Nightmare Alarm"
            textSize = 24f
        }

        statusText = TextView(this).apply {
            text = "Status: stopped"
            textSize = 16f
            setPadding(0, 32, 0, 32)
        }

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
        layout.addView(startBtn)
        layout.addView(stopBtn)
        layout.addView(batteryBtn)
        layout.addView(autostartBtn)
        setContentView(layout)

        requestRuntimePermissions()
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
        statusText.text = "Status: LISTENING (threshold ~65 dB)"
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
