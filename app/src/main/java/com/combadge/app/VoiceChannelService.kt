package com.combadge.app

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Foreground service with three roles:
 *
 *  1. **Online** (ACTION_START_ONLINE) — persistent low-profile notification that keeps the
 *     process alive so the SignalingServer in the ViewModel can receive hails even when the
 *     app is backgrounded.
 *
 *  2. **Incoming hail** (ACTION_INCOMING_HAIL) — upgrades to a high-priority heads-up
 *     notification (with full-screen intent where permitted) so the user is alerted even
 *     when the device screen is off or the app is not in focus.
 *
 *  3. **Channel open** (ACTION_START_CHANNEL) — shows a "connected to <peer>" notification
 *     while a voice channel is active.
 *
 * Use ACTION_STOP to dismiss all notifications and stop the service.
 */
class VoiceChannelService : Service() {

    companion object {
        private const val TAG = "VoiceChannelService"

        // Notification channels
        private const val CHANNEL_ONLINE = "combadge_online"
        private const val CHANNEL_HAIL   = "combadge_hail"
        private const val CHANNEL_VOICE  = "combadge_voice"

        // Notification IDs (only one shown at a time; we reuse ID 1)
        private const val NOTIF_ID = 1

        // Intent actions
        const val ACTION_START_ONLINE    = "com.combadge.app.ACTION_START_ONLINE"
        const val ACTION_INCOMING_HAIL   = "com.combadge.app.ACTION_INCOMING_HAIL"
        const val ACTION_START_CHANNEL   = "com.combadge.app.ACTION_START_CHANNEL"
        const val ACTION_STOP            = "com.combadge.app.ACTION_STOP"

        // Back-compat aliases used by older callers
        const val ACTION_START           = ACTION_START_CHANNEL
        const val ACTION_STOP_CHANNEL    = ACTION_STOP

        // Intent extras
        const val EXTRA_PEER_NAME = "peer_name"
        const val EXTRA_PHRASE    = "phrase"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ONLINE -> {
                Log.d(TAG, "Starting online (keep-alive) foreground notification")
                startForeground(NOTIF_ID, buildOnlineNotification())
            }
            ACTION_INCOMING_HAIL -> {
                val from   = intent.getStringExtra(EXTRA_PEER_NAME) ?: "Unknown"
                val phrase = intent.getStringExtra(EXTRA_PHRASE)    ?: "$from to you"
                Log.d(TAG, "Incoming hail from $from: \"$phrase\"")
                // Show/update as high-priority — wakes screen / shows heads-up
                startForeground(NOTIF_ID, buildHailNotification(phrase, from))
            }
            ACTION_START_CHANNEL -> {
                val peerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: "Unknown"
                Log.d(TAG, "Channel open with $peerName")
                startForeground(NOTIF_ID, buildChannelNotification(peerName))
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stopping service")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    // ------------------------------------------------------------------ //
    // Notification builders
    // ------------------------------------------------------------------ //

    /** Subtle always-on notification — keeps process alive. */
    private fun buildOnlineNotification(): Notification {
        val tapIntent = mainActivityIntent()
        return NotificationCompat.Builder(this, CHANNEL_ONLINE)
            .setContentTitle("Combadge online")
            .setContentText("Listening for hails")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** High-priority heads-up + full-screen intent so it surfaces on a locked/dark screen. */
    private fun buildHailNotification(phrase: String, from: String): Notification {
        val tapIntent = mainActivityIntent()

        val builder = NotificationCompat.Builder(this, CHANNEL_HAIL)
            .setContentTitle("Incoming hail from $from")
            .setContentText(phrase)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(tapIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // Full-screen intent: brings the app to the foreground even from lock screen
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasFullScreenIntentPermission())
        ) {
            builder.setFullScreenIntent(tapIntent, true)
        }

        return builder.build()
    }

    /** Ongoing notification shown while a voice channel is active. */
    private fun buildChannelNotification(peerName: String): Notification {
        val tapIntent = mainActivityIntent()
        return NotificationCompat.Builder(this, CHANNEL_VOICE)
            .setContentTitle("Voice Channel Active")
            .setContentText("Connected to $peerName")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun mainActivityIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    @Suppress("DEPRECATION")
    private fun hasFullScreenIntentPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.canUseFullScreenIntent()
        } else {
            true
        }
    }

    // ------------------------------------------------------------------ //
    // Notification channels
    // ------------------------------------------------------------------ //

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONLINE, "Combadge Online", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Persistent keep-alive notification while Combadge is registered"
                setShowBadge(false)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_HAIL, "Incoming Hails", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alert when another crew member hails you"
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 50, 100, 50)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_VOICE, "Voice Channel", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while a voice channel is active"
                setShowBadge(false)
            }
        )
    }
}
