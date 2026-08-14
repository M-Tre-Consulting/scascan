package com.scascan.app.domain.usecase

import com.scascan.app.data.model.NutritionFacts
import com.scascan.app.data.repository.NutritionRepository
import javax.inject.Inject

class ScanBarcodeUseCase @Inject constructor(
    private val repository: NutritionRepository
) {
    suspend operator fun invoke(barcode: String): Result<NutritionFacts> =
        repository.analyzeBarcode(barcode)
}
