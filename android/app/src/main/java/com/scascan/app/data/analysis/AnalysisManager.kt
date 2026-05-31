package com.scascan.app.data.analysis

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.scascan.app.MainActivity
import com.scascan.app.R
import com.scascan.app.ScaScanApplication
import com.scascan.app.data.model.NutritionFacts
import com.scascan.app.data.repository.NutritionRepository
import com.scascan.app.data.worker.AnalysisWorker
import com.scascan.app.ui.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalysisManager @Inject constructor(
    private val nutritionRepository: NutritionRepository,
    private val notificationHelper: NotificationHelper,
    @param:ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workManager = WorkManager.getInstance(context)

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
            try {
                // Save bitmap to temp file for worker
                val tempFile = File(context.cacheDir, "analysis_${UUID.randomUUID()}.jpg")
                FileOutputStream(tempFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                val workRequest = OneTimeWorkRequestBuilder<AnalysisWorker>()
                    .setInputData(Data.Builder().putString(AnalysisWorker.KEY_IMAGE_PATH, tempFile.absolutePath).build())
                    .build()

                workManager.enqueue(workRequest)

                // Track work state (optional, for UI feedback)
                // Note: AnalysisWorker current implementation also does direct analysis for in-app flow
                // For immediate UI, we can still run it here or observe workManager
                
                // Let's keep the immediate flow for foreground and use worker for robustness/background
                nutritionRepository.analyzeImage(bitmap)
                    .onSuccess { facts ->
                        _state.value = State.Complete(facts)
                        val app = context.applicationContext as ScaScanApplication
                        if (!app.isForeground) {
                            notificationHelper.postAnalysisComplete(facts)
                        }
                    }
                    .onFailure { e ->
                        _state.value = State.Error(e.message ?: context.getString(R.string.analysis_error_generic))
                    }
            } catch (e: Exception) {
                _state.value = State.Error(e.message ?: "Failed to start analysis")
            }
        }
    }

    fun dismiss() {
        _state.value = State.Idle
    }
}
