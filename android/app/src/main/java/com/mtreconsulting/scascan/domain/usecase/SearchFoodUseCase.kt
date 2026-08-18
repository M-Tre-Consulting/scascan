package com.mtreconsulting.scascan.domain.usecase

import com.mtreconsulting.scascan.data.model.NutritionFacts
import com.mtreconsulting.scascan.data.repository.NutritionRepository
import javax.inject.Inject

class SearchFoodUseCase @Inject constructor(
    private val repository: NutritionRepository
) {
    suspend operator fun invoke(query: String): Result<NutritionFacts> =
        repository.searchFood(query)
}
