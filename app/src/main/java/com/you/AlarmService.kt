package com.you.nightmarealarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    private lateinit var watcher: SoundWatcher
    private lateinit var alarm: LoudAlarmController
    private val handler = Handler(Looper.getMainLooper())
    private var alarmActive = false
    private var monitorStarted = false

    private val startReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_START_MONITORING) {
                if (::watcher.isInitialized) startMonitoring()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        alarm = LoudAlarmController(this)

        val filter = IntentFilter(ACTION_START_MONITORING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(startReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(startReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()

        if (monitorStarted) {
            updateNotification("Listening for vocalizations...")
            return START_STICKY
        }

        val prefs = getSharedPreferences("nightmare_prefs", Context.MODE_PRIVATE)
        val threshold = prefs.getInt("threshold_db", 65).toDouble()
        val delayEnabled = prefs.getBoolean("delay_enabled", false)
        val delayMinutes = if (delayEnabled) prefs.getInt("delay_minutes", 60) else 0

        watcher = SoundWatcher(thresholdDb = threshold) {
            handler.post {
                if (!alarmActive) {
                    alarmActive = true
                    alarm.triggerAlarm()
                }
            }
        }

        if (delayMinutes > 0) {
            scheduleDelayedStart(delayMinutes)
            updateNotification("Monitoring starts in $delayMinutes min")
        } else {
            startMonitoring()
        }

        return START_STICKY
    }

    private fun scheduleDelayedStart(delayMinutes: Int) {
        val triggerAtMillis = SystemClock.elapsedRealtime() + delayMinutes.toLong() * 60_000L

        val intent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_START_MONITORING
        }
        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pi = PendingIntent.getService(this, 42, intent, piFlags)

        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pi
                )
            } else {
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                pi
            )
        }
    }

    private fun startMonitoring() {
        if (monitorStarted) return
        if (!::watcher.isInitialized) return
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
        try { unregisterReceiver(startReceiver) } catch (_: Exception) {}
        try { if (monitorStarted && ::watcher.isInitialized) watcher.stop() } catch (_: Exception) {}
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

    companion object {
        const val CHANNEL_ID = "nightmare_alarm_channel"
        const val ACTION_START_MONITORING = "com.you.nightmarealarm.START_MONITORING"
    }
}
