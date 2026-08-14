package com.scascan.app.domain.usecase

import com.scascan.app.data.model.NutritionFacts
import com.scascan.app.data.repository.NutritionRepository
import javax.inject.Inject

class SearchFoodUseCase @Inject constructor(
    private val repository: NutritionRepository
) {
    suspend operator fun invoke(query: String): Result<NutritionFacts> =
        repository.searchFood(query)
}
