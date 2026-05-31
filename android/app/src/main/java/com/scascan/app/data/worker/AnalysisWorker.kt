package com.scascan.app.data.worker

import android.content.Context
import android.graphics.BitmapFactory
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.scascan.app.ScaScanApplication
import com.scascan.app.data.repository.NutritionRepository
import com.scascan.app.ui.util.NotificationHelper
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
        val imagePath = inputData.getString(KEY_IMAGE_PATH) ?: return Result.failure()
        val file = File(imagePath)
        if (!file.exists()) return Result.failure()

        return try {
            val bitmap = BitmapFactory.decodeFile(imagePath)
            val factsResult = nutritionRepository.analyzeImage(bitmap)
            
            // Clean up temporary image
            file.delete()
            
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
                onFailure = { 
                    Result.failure()
                }
            )
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_IMAGE_PATH = "image_path"
    }
}
