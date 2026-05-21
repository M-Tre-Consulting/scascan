package com.scascan.app.domain.usecase

import android.graphics.Bitmap
import com.scascan.app.data.model.NutritionFacts
import com.scascan.app.data.repository.NutritionRepository
import javax.inject.Inject

class AnalyzeFoodImageUseCase @Inject constructor(
    private val repository: NutritionRepository
) {
    suspend operator fun invoke(bitmap: Bitmap): Result<NutritionFacts> =
        repository.analyzeImage(bitmap)
}
