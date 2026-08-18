package com.mtreconsulting.scascan.data.worker

import android.content.Context
import android.graphics.BitmapFactory
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mtreconsulting.scascan.ScaScanApplication
import com.mtreconsulting.scascan.data.repository.NutritionRepository
import com.mtreconsulting.scascan.ui.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class AnalysisWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val nutritionRepository: NutritionRepository,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val imagePath = inputData.getString(KEY_IMAGE_PATH)
        val query = inputData.getString(KEY_SEARCH_QUERY)
        val isBarcode = inputData.getBoolean(KEY_IS_BARCODE, false)
        
        // Fix params
        val fixName = inputData.getString(KEY_FIX_ORIGINAL_NAME)
        val fixSize = inputData.getString(KEY_FIX_ORIGINAL_SIZE)
        val fixCorrection = inputData.getString(KEY_FIX_CORRECTION)

        return try {
            val factsResult = when {
                fixName != null && fixSize != null && fixCorrection != null -> {
                    nutritionRepository.fixEntry(fixName, fixSize, fixCorrection)
                }
                imagePath != null -> {
                    val file = File(imagePath)
                    if (!file.exists()) return Result.failure()
                    val bitmap = BitmapFactory.decodeFile(imagePath)
                    val result = if (isBarcode) {
                        nutritionRepository.analyzeBarcodeImage(bitmap)
                    } else {
                        nutritionRepository.analyzeImage(bitmap)
                    }
                    file.delete()
                    result
                }
                query != null -> {
                    nutritionRepository.searchFood(query)
                }
                else -> return Result.failure()
            }
            
            factsResult.fold(
                onSuccess = { facts ->
                    val app = applicationContext as ScaScanApplication
                    if (!app.isForeground) {
                        notificationHelper.postAnalysisComplete(facts)
                    }
                    
                    val outputData = androidx.work.workDataOf(
                        "facts_json" to com.google.gson.Gson().toJson(facts)
                    )
                    Result.success(outputData)
                },
                onFailure = { throwable ->
                    val errorData = androidx.work.workDataOf(
                        "error_message" to (throwable.message ?: "Analysis failed")
                    )
                    Result.failure(errorData)
                }
            )
        } catch (e: Exception) {
            val errorData = androidx.work.workDataOf(
                "error_message" to (e.message ?: "Unexpected error during analysis")
            )
            Result.failure(errorData)
        }
    }

    companion object {
        const val KEY_IMAGE_PATH = "image_path"
        const val KEY_SEARCH_QUERY = "search_query"
        const val KEY_IS_BARCODE = "is_barcode"
        
        const val KEY_FIX_ORIGINAL_NAME = "fix_original_name"
        const val KEY_FIX_ORIGINAL_SIZE = "fix_original_size"
        const val KEY_FIX_CORRECTION = "fix_correction"
    }
}
