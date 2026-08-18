package com.mtreconsulting.scascan.ui.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mtreconsulting.scascan.MainActivity
import com.mtreconsulting.scascan.R
import com.mtreconsulting.scascan.data.model.NutritionFacts
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)

        // 1. Food Analysis channel
        val analysisChannel = NotificationChannel(
            CHANNEL_ANALYSIS,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
        }

        // 2. Reminders channel
        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.notif_channel_reminders_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_reminders_desc)
        }

        nm.createNotificationChannels(listOf(analysisChannel, reminderChannel))
    }

    fun postAnalysisComplete(facts: NutritionFacts) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = MainActivity.ACTION_SHOW_ANALYSIS
            putExtra(MainActivity.EXTRA_FACTS_JSON, com.google.gson.Gson().toJson(facts))
        }
        val pending = PendingIntent.getActivity(
            context, 100, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ANALYSIS)
            .setSmallIcon(R.drawable.ic_nav_log)
            .setContentTitle(context.getString(R.string.notif_analysis_title))
            .setContentText(
                context.getString(
                    R.string.notif_analysis_text,
                    facts.foodName,
                    facts.calories.toInt()
                )
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notifySafely(NOTIF_ID_ANALYSIS, notification)
    }

    fun postHydrationReminder() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = MainActivity.ACTION_OPEN_LOG
        }
        val pending = PendingIntent.getActivity(
            context, 101, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_nav_log)
            .setContentTitle(context.getString(R.string.notif_hydration_title))
            .setContentText(context.getString(R.string.notif_hydration_text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        notifySafely(NOTIF_ID_REMINDER, notification)
    }

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission not granted on API 33+
        }
    }

    companion object {
        const val CHANNEL_ANALYSIS = "scascan_analysis"
        const val CHANNEL_REMINDERS = "scascan_reminders"
        
        private const val NOTIF_ID_ANALYSIS = 1001
        private const val NOTIF_ID_REMINDER = 1002
    }
}
