package com.scascan.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.scascan.app.ui.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_WATER_REMINDER) {
            notificationHelper.postHydrationReminder()
        }
    }

    companion object {
        const val ACTION_WATER_REMINDER = "com.scascan.app.ACTION_WATER_REMINDER"
    }
}
