package com.example.recemotion.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra("todoId", -1L)
        val title = intent.getStringExtra("title") ?: "リマインド"
        val description = intent.getStringExtra("description") ?: ""
        SimpleNotification.showReminder(context, todoId.toInt(), title, description)
    }
}
