package com.scascan.app.data.analysis

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.scascan.app.MainActivity
import com.scascan.app.R
import com.scascan.app.ScaScanApplication
import com.scascan.app.data.model.NutritionFacts
import com.scascan.app.data.repository.NutritionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalysisManager @Inject constructor(
    private val nutritionRepository: NutritionRepository,
    @param:ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    sealed class State {
        object Idle : State()
        object Processing : State()
        data class Complete(val facts: NutritionFacts) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    fun analyze(bitmap: Bitmap) {
        if (_state.value is State.Processing) return
        _state.value = State.Processing
        scope.launch {
            nutritionRepository.analyzeImage(bitmap)
                .onSuccess { facts ->
                    _state.value = State.Complete(facts)
                    val app = context.applicationContext as ScaScanApplication
                    if (!app.isForeground) postNotification(facts)
                }
                .onFailure { e ->
                    _state.value = State.Error(e.message ?: context.getString(R.string.analysis_error_generic))
                }
        }
    }

    fun dismiss() {
        _state.value = State.Idle
    }

    private fun postNotification(facts: NutritionFacts) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
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
            .build()

        try {
            nm.notify(NOTIF_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted on API 33+
        }
    }

    companion object {
        const val CHANNEL_ID = "scascan_analysis"
        private const val NOTIF_ID = 1001
    }
}
