package com.you.nightmarealarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    private lateinit var watcher: SoundWatcher
    private lateinit var alarm: LoudAlarmController
    private val handler = Handler(Looper.getMainLooper())
    private var alarmActive = false
    private var monitorStarted = false
    private var delayMinutes = 0
    private var remainingSeconds = 0L

    override fun onCreate() {
        super.onCreate()
        alarm = LoudAlarmController(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()

        val prefs = getSharedPreferences("nightmare_prefs", Context.MODE_PRIVATE)
        val threshold = prefs.getInt("threshold_db", 65).toDouble()
        val delayEnabled = prefs.getBoolean("delay_enabled", false)
        delayMinutes = if (delayEnabled) prefs.getInt("delay_minutes", 60) else 0

        watcher = SoundWatcher(thresholdDb = threshold) {
            handler.post {
                if (!alarmActive) {
                    alarmActive = true
                    alarm.triggerAlarm()
                }
            }
        }

        if (delayMinutes > 0) {
            remainingSeconds = delayMinutes.toLong() * 60L
            updateNotification("Waiting $delayMinutes min before monitoring...")
            startCountdown()
        } else {
            startMonitoring()
        }

        return START_STICKY
    }

    private fun startCountdown() {
        handler.post(object : Runnable {
            override fun run() {
                if (remainingSeconds <= 0) {
                    startMonitoring()
                    return
                }
                val min = remainingSeconds / 60
                val sec = remainingSeconds % 60
                updateNotification("Monitoring starts in %02d:%02d".format(min, sec))
                remainingSeconds -= 5
                handler.postDelayed(this, 5000)
            }
        })
    }

    private fun startMonitoring() {
        if (monitorStarted) return
        monitorStarted = true
        updateNotification("Listening for vocalizations...")
        watcher.start()
    }

    private fun updateNotification(text: String) {
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nightmare Alarm")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notif)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { if (monitorStarted) watcher.stop() } catch (_: Exception) {}
        try { alarm.stopAlarm() } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Nightmare Alarm",
                    NotificationManager.IMPORTANCE_LOW
                )
                nm.createNotificationChannel(ch)
            }
        }
    }

    companion object { const val CHANNEL_ID = "nightmare_alarm_channel" }
}
