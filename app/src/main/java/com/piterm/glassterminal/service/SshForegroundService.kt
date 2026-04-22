package com.piterm.glassterminal.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.piterm.glassterminal.MainActivity
import com.piterm.glassterminal.R

/**
 * Foreground service to keep the SSH connection alive when the app is backgrounded.
 * Android requires a persistent notification for long-running network operations.
 */
class SshForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "glassterminal_ssh"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.piterm.glassterminal.STOP_SSH"

        /**
         * Static callback for when the user presses "Disconnect" in the notification.
         * Set by SshConnectionManager when starting the service.
         */
        var onDisconnectRequested: (() -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Tear down the SSH tunnel before stopping the service
            onDisconnectRequested?.invoke()
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SSH Connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the SSH tunnel to your Pi alive"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, SshForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Glass Terminal")
            .setContentText("SSH tunnel active — Pi connected")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", stopPending)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
