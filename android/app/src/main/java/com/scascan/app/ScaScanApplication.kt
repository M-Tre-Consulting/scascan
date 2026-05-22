package com.scascan.app

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import com.scascan.app.data.analysis.AnalysisManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ScaScanApplication : Application() {

    var isForeground = false
        private set

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(ForegroundTracker())
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            AnalysisManager.CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notif_channel_desc)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private inner class ForegroundTracker : ActivityLifecycleCallbacks {
        private var startedCount = 0
        override fun onActivityStarted(a: Activity) { if (++startedCount == 1) isForeground = true }
        override fun onActivityStopped(a: Activity) { if (--startedCount == 0) isForeground = false }
        override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
        override fun onActivityResumed(a: Activity) = Unit
        override fun onActivityPaused(a: Activity) = Unit
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
        override fun onActivityDestroyed(a: Activity) = Unit
    }
}
