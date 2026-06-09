package com.scascan.app.data.analysis

import android.content.Context
import android.graphics.Bitmap
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.scascan.app.R
import com.scascan.app.data.model.NutritionFacts
import com.scascan.app.data.worker.AnalysisWorker
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
        startWorker(bitmap = bitmap, isBarcode = false)
    }

    fun analyzeBarcode(bitmap: Bitmap) {
        startWorker(bitmap = bitmap, isBarcode = true)
    }

    fun analyzeSearch(query: String) {
        startWorker(query = query)
    }

    private fun startWorker(bitmap: Bitmap? = null, query: String? = null, isBarcode: Boolean = false) {
        if (_state.value is State.Processing) return
        _state.value = State.Processing
        
        scope.launch {
            try {
                val builder = Data.Builder()
                
                bitmap?.let {
                    // Save bitmap to temp file for worker
                    val tempFile = File(context.cacheDir, "analysis_${UUID.randomUUID()}.jpg")
                    FileOutputStream(tempFile).use { out ->
                        it.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    builder.putString(AnalysisWorker.KEY_IMAGE_PATH, tempFile.absolutePath)
                    builder.putBoolean(AnalysisWorker.KEY_IS_BARCODE, isBarcode)
                }

                query?.let {
                    builder.putString(AnalysisWorker.KEY_SEARCH_QUERY, it)
                }

                val workRequest = OneTimeWorkRequestBuilder<AnalysisWorker>()
                    .setInputData(builder.build())
                    .build()

                workManager.enqueue(workRequest)

                // Observe work to update state in the foreground
                scope.launch(Dispatchers.Main) {
                    workManager.getWorkInfoByIdFlow(workRequest.id).collect { workInfo ->
                        when (workInfo?.state) {
                            WorkInfo.State.SUCCEEDED -> {
                                val json = workInfo.outputData.getString("facts_json")
                                if (json != null) {
                                    val facts = com.google.gson.Gson().fromJson(json, NutritionFacts::class.java)
                                    _state.value = State.Complete(facts)
                                } else {
                                    _state.value = State.Error("Failed to parse analysis result")
                                }
                            }
                            WorkInfo.State.FAILED -> {
                                val error = workInfo.outputData.getString("error_message")
                                    ?: context.getString(R.string.analysis_error_generic)
                                _state.value = State.Error(error)
                            }
                            else -> { /* Stay in processing */ }
                        }
                    }
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
