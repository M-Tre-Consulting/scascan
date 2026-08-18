package com.mtreconsulting.scascan.domain.usecase

import com.mtreconsulting.scascan.data.model.NutritionFacts
import com.mtreconsulting.scascan.data.repository.NutritionRepository
import javax.inject.Inject

class ScanBarcodeUseCase @Inject constructor(
    private val repository: NutritionRepository
) {
    suspend operator fun invoke(barcode: String): Result<NutritionFacts> =
        repository.analyzeBarcode(barcode)
}
