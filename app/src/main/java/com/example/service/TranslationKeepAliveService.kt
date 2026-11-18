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
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

private const val TAG = "TranslationKeepAliveSvc"
private const val CHANNEL_ID = "bayan_translation_channel"
private const val NOTIFICATION_ID = 3042
private const val ACTION_START_OR_UPDATE = "com.example.action.TRANSLATION_PROGRESS"
private const val ACTION_STOP = "com.example.action.TRANSLATION_STOP"
private const val EXTRA_TITLE = "extra_title"
private const val EXTRA_STATUS = "extra_status"
private const val EXTRA_PROGRESS = "extra_progress"
private const val EXTRA_MAX = "extra_max"

class TranslationKeepAliveService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "بيان يترجم الآن"
        val status = intent?.getStringExtra(EXTRA_STATUS) ?: "جاري المعالجة في الخلفية…"
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
        val max = intent?.getIntExtra(EXTRA_MAX, 0) ?: 0

        acquireWakeLockIfNeeded()
        val notification = buildNotification(title, status, progress, max)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to promote to foreground service", e)
        }

        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground service timeout reached for fgsType=$fgsType, stopping cleanly")
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLockIfNeeded() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Bayan:TranslationWakeLock").apply {
                setReferenceCounted(false)
                // Safety timeout of 30 minutes max per active job
                acquire(30L * 60L * 1000L)
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "ترجمة الفيديوهات في الخلفية",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "يبقي تطبيق بيان نشطاً أثناء معالجة وترجمة المقاطع الطويلة."
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(
        title: String,
        status: String,
        progress: Int,
        max: Int
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(status)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (max > 0) {
            builder.setProgress(max, progress.coerceIn(0, max), false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    companion object {
        fun updateProgress(
            context: Context,
            title: String,
            status: String,
            progress: Int = 0,
            max: Int = 0
        ) {
            runCatching {
                val intent = Intent(context, TranslationKeepAliveService::class.java).apply {
                    action = ACTION_START_OR_UPDATE
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_STATUS, status)
                    putExtra(EXTRA_PROGRESS, progress)
                    putExtra(EXTRA_MAX, max)
                }
                ContextCompat.startForegroundService(context, intent)
            }.onFailure {
                Log.w(TAG, "ForegroundService start skipped", it)
            }
        }

        fun stop(context: Context) {
            runCatching {
                val intent = Intent(context, TranslationKeepAliveService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            }
        }
    }
}
