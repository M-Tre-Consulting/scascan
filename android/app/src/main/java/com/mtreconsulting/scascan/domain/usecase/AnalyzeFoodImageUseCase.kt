package com.mtreconsulting.scascan.domain.usecase

import android.graphics.Bitmap
import com.mtreconsulting.scascan.data.model.NutritionFacts
import com.mtreconsulting.scascan.data.repository.NutritionRepository
import javax.inject.Inject

class AnalyzeFoodImageUseCase @Inject constructor(
    private val repository: NutritionRepository
) {
    suspend operator fun invoke(bitmap: Bitmap): Result<NutritionFacts> =
        repository.analyzeImage(bitmap)
}
