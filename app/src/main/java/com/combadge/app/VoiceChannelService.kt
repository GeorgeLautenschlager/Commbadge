package com.combadge.app

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps the voice channel alive when the screen is off.
 *
 * Started when a voice channel opens; stopped when it closes.
 */
class VoiceChannelService : Service() {

    companion object {
        private const val TAG = "VoiceChannelService"
        private const val CHANNEL_ID = "combadge_voice_channel"
        private const val NOTIFICATION_ID = 1

        const val EXTRA_PEER_NAME = "peer_name"
        const val ACTION_START = "com.combadge.app.ACTION_START_CHANNEL"
        const val ACTION_STOP  = "com.combadge.app.ACTION_STOP_CHANNEL"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val peerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: "Unknown"
                Log.d(TAG, "Starting foreground service for channel with $peerName")
                startForeground(NOTIFICATION_ID, buildNotification(peerName))
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stopping foreground service")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(peerName: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Channel Active")
            .setContentText("Connected to $peerName")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Combadge active voice channel"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
