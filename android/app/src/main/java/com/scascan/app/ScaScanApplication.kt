package com.scascan.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.work.Configuration
import com.scascan.app.ui.util.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import androidx.hilt.work.HiltWorkerFactory

@HiltAndroidApp
class ScaScanApplication : Application(), Configuration.Provider {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    var isForeground = false
        private set

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(ForegroundTracker())
        notificationHelper.createChannels()
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
