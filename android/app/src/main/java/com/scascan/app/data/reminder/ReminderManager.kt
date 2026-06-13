package com.scascan.app.data.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.scascan.app.data.worker.HydrationReminderWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    fun scheduleHydrationReminder(intervalMillis: Long) {
        val workRequest = PeriodicWorkRequestBuilder<HydrationReminderWorker>(
            intervalMillis, TimeUnit.MILLISECONDS
        ).build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME_HYDRATION,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun cancelHydrationReminder() {
        workManager.cancelUniqueWork(WORK_NAME_HYDRATION)
    }

    companion object {
        private const val WORK_NAME_HYDRATION = "hydration_reminder"
    }
}
