package com.scooterre.client.service

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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import com.scooterre.client.MainActivity

/**
 * Keeps the app's process in the foreground while a scooter is connected. The connection, the read schedule and the
 * ride recording all live inside the app's process; without this service Android (MIUI/HyperOS especially) may
 * throttle or end that process as soon as the app is in the background or the screen is off, and the connection with
 * it. The service does no work of its own - it only shows the ongoing "connected" notification that makes Android
 * treat the process as in use.
 */
class ConnectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var overlay: OverlayController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        overlay = OverlayController(this, scope).also { it.start() }
    }

    override fun onDestroy() {
        overlay?.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, intent?.getStringExtra(EXTRA_CHANNEL) ?: "Scooter", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(intent?.getStringExtra(EXTRA_TITLE) ?: "Scooter Link")
            .setContentText(intent?.getStringExtra(EXTRA_TEXT) ?: "")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Not allowed right now (e.g. started from the background on a newer Android): the app then just runs without it.
            android.util.Log.w(TAG, "startForeground refused", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "ConnectionService"
        private const val CHANNEL_ID = "scooter_connection"
        private const val NOTIFICATION_ID = 4711
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_CHANNEL = "channel"

        fun start(context: Context, title: String, text: String, channelName: String) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ConnectionService::class.java)
                        .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text).putExtra(EXTRA_CHANNEL, channelName),
                )
            } catch (e: Exception) {
                android.util.Log.w(TAG, "could not start the connection service", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, ConnectionService::class.java))
            } catch (e: Exception) {
                android.util.Log.w(TAG, "could not stop the connection service", e)
            }
        }
    }
}
