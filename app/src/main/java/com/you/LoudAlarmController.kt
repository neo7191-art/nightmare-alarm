package com.you.nightmarealarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

class LoudAlarmController(
    private val context: Context,
    private val onShakeStop: () -> Unit = {}
) {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var shakeListener: SensorEventListener? = null

    // Tune: 8.0 = sensitive, 12.0 = default, 18.0 = firm shake
    private val SHAKE_THRESHOLD = 12.0f

    fun triggerAlarm() {
        stopAlarm()

        val prefs = context.getSharedPreferences("nightmare_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getString("output_mode", "both") ?: "both"
        val shakeEnabled = prefs.getBoolean("shake_to_stop", true)

        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0
        )
        am.setStreamVolume(
            AudioManager.STREAM_ALARM,
            am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0
        )

        if (mode == "sound" || mode == "both") {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val customUri = prefs.getString("tone_uri", null)
            val uri: Uri = if (customUri != null) Uri.parse(customUri)
                            else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(context, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (_: Exception) {
                val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                player = MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(context, fallback)
                    isLooping = true
                    prepare()
                    start()
                }
            }
        }

        if (mode == "vibrate" || mode == "both") {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }

        if (shakeEnabled) {
            startShakeDetector()
        }
    }

    private fun startShakeDetector() {
        try {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accelerometer == null) {
                Log.d("NightmareAlarm", "No accelerometer")
                return
            }

            var lastShakeTime = 0L

            shakeListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event == null) return

                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]

                    val gForce = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                    val movement = abs(gForce - SensorManager.GRAVITY_EARTH)

                    if (movement > SHAKE_THRESHOLD) {
                        val now = System.currentTimeMillis()
                        if (now - lastShakeTime > 1500) {
                            lastShakeTime = now
                            Log.d("NightmareAlarm", "Shake detected ($movement) — stopping")
                            stopAlarm()
                            onShakeStop()
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            sensorManager?.registerListener(
                shakeListener,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            Log.d("NightmareAlarm", "Shake detector active")
        } catch (e: Exception) {
            Log.d("NightmareAlarm", "Shake detector failed: ${e.message}")
        }
    }

    fun stopAlarm() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null

        try { vibrator?.cancel() } catch (_: Exception) {}
        vibrator = null

        try {
            shakeListener?.let { sensorManager?.unregisterListener(it) }
        } catch (_: Exception) {}
        shakeListener = null
    }
}
