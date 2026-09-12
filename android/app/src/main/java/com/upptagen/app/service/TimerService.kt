package com.upptagen.app.service

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
import androidx.core.content.ContextCompat
import com.upptagen.app.MainActivity
import com.upptagen.app.R
import com.upptagen.app.UpptagenApplication
import com.upptagen.app.data.model.ActiveTimerEntity
import com.upptagen.app.data.repository.TimeTrackingRepository
import com.upptagen.app.util.DateTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TimerService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var observerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeActiveTimer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_TIMER -> {
                serviceScope.launch {
                    val app = application as? UpptagenApplication
                    app?.repository?.stopTimer()
                }
            }
            ACTION_START_SERVICE -> {
                serviceScope.launch {
                    val app = (application as? UpptagenApplication) ?: return@launch
                    val timer = app.repository.getActiveTimer()
                    if (timer != null) {
                        showForegroundNotification(timer)
                    } else {
                        stopServiceForeground()
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active Timer",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Live notification showing ongoing task timer"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun observeActiveTimer() {
        observerJob?.cancel()
        observerJob = serviceScope.launch {
            val app = (application as? UpptagenApplication) ?: return@launch
            app.repository.getActiveTimerFlow().collectLatest { activeTimer ->
                if (activeTimer != null) {
                    showForegroundNotification(activeTimer)
                } else {
                    stopServiceForeground()
                }
            }
        }
    }

    private fun showForegroundNotification(activeTimer: ActiveTimerEntity) {
        val startInstant = DateTimeUtils.parseIsoOrFlexible(activeTimer.startTime)
        val startEpochMillis = startInstant?.toEpochMilli() ?: System.currentTimeMillis()

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TimerService::class.java).apply {
            action = ACTION_STOP_TIMER
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val projectTitle = activeTimer.project?.ifBlank { null } ?: "Active Task"
        val desc = activeTimer.description?.ifBlank { null }
        val tags = TimeTrackingRepository.jsonToTags(activeTimer.tagsJson)
        val tagsFormatted = if (tags.isNotEmpty()) tags.joinToString(" ") { "#$it" } else null

        val subtitle = listOfNotNull(desc, tagsFormatted).joinToString(" • ").ifEmpty { "Tracking time" }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(projectTitle)
            .setContentText(subtitle)
            .setUsesChronometer(true)
            .setWhen(startEpochMillis)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_timer_notification,
                "Stop",
                stopPendingIntent
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopServiceForeground() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "timer_tracking_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_SERVICE = "com.upptagen.app.action.START_TIMER_SERVICE"
        const val ACTION_STOP_TIMER = "com.upptagen.app.action.STOP_TIMER"

        fun startService(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
