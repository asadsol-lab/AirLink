package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScreenCaptureService : Service() {

    companion object {
        private const val CHANNEL_ID = "airlink_screen_capture_channel"
        private const val NOTIFICATION_ID = 4102
        const val ACTION_START_CAPTURE = "com.example.action.START_CAPTURE"
        const val ACTION_STOP_CAPTURE = "com.example.action.STOP_CAPTURE"
        const val ACTION_EMERGENCY_DISCONNECT = "com.example.action.EMERGENCY_DISCONNECT"

        private val _isForegroundActive = MutableStateFlow(false)
        val isForegroundActive: StateFlow<Boolean> = _isForegroundActive.asStateFlow()

        private val _emergencyStopEvents = MutableStateFlow(0L)
        val emergencyStopEvents: StateFlow<Long> = _emergencyStopEvents.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START_CAPTURE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            _isForegroundActive.value = false
            context.stopService(Intent(context, ScreenCaptureService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_EMERGENCY_DISCONNECT -> {
                _emergencyStopEvents.value = System.currentTimeMillis()
                stopSelfAndReset()
                return START_NOT_STICKY
            }

            ACTION_STOP_CAPTURE -> {
                stopSelfAndReset()
                return START_NOT_STICKY
            }

            else -> {
                createNotificationChannel()
                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                _isForegroundActive.value = true
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        _isForegroundActive.value = false
    }

    private fun stopSelfAndReset() {
        _isForegroundActive.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.capture_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.capture_notification_text)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val emergencyIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenCaptureService::class.java).apply {
                action = ACTION_EMERGENCY_DISCONNECT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.emergency_disconnect),
                emergencyIntent
            )
            .build()
    }
}
