package com.example.recemotion

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ForegroundInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MessageSendWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo())

        val target = inputData.getString("target") ?: return Result.failure()
        val sender = inputData.getString("sender") ?: "Android"
        val message = inputData.getString("message") ?: return Result.failure()

        // Call JNI (Blocking) in IO Dispatcher
        val success = withContext(Dispatchers.IO) {
             MainActivity.sendMessage(target, sender, message)
        }

        return if (success) Result.success() else Result.failure()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val channelId = "p2p_channel"
        val title = "Sending P2P Message"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, title, NotificationManager.IMPORTANCE_LOW)
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setTicker(title)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        
        return ForegroundInfo(1, notification)
    }
}
