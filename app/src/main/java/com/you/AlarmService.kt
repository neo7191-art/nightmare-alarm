package com.you.nightmarealarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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

    override fun onCreate() {
        super.onCreate()
        alarm = LoudAlarmController(this)

        watcher = SoundWatcher(thresholdDb = 65.0) {
            handler.post {
                if (!alarmActive) {
                    alarmActive = true
                    alarm.triggerAlarm()
                    handler.postDelayed({
                        alarm.stopAlarm()
                        alarmActive = false
                    }, 15_000)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nightmare Alarm active")
            .setContentText("Listening for vocalizations...")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notif)
        }

        watcher.start()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { watcher.stop() } catch (_: Exception) {}
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
