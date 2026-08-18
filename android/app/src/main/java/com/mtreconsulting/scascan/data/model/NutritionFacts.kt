package com.mtreconsulting.scascan.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class NutritionFacts(
    val foodName: String,
    val servingSize: String,
    val calories: Double,
    val protein: Double,
    val carbohydrates: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double,
    val sodium: Double
) : Parcelable
