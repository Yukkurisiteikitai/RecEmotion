package com.example.recemotion.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object ReminderScheduler {

    private const val ACTION = "com.example.recemotion.REMINDER_ALARM"

    fun schedule(
        context: Context,
        todoId: Long,
        title: String,
        description: String,
        triggerAtMillis: Long
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, todoId, title, description)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            return
        }
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    /** テスト用: 5秒後に発火 */
    fun scheduleNow(context: Context, title: String, description: String) {
        schedule(
            context,
            todoId = System.currentTimeMillis(),
            title = title,
            description = description,
            triggerAtMillis = System.currentTimeMillis() + 5_000L
        )
    }

    fun cancel(context: Context, todoId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, todoId, "", "")
        alarmManager.cancel(pendingIntent)
    }

    private fun buildPendingIntent(
        context: Context,
        todoId: Long,
        title: String,
        description: String
    ): PendingIntent {
        val intent = Intent(ACTION).apply {
            setPackage(context.packageName)
            putExtra("todoId", todoId)
            putExtra("title", title)
            putExtra("description", description)
        }
        return PendingIntent.getBroadcast(
            context,
            todoId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
