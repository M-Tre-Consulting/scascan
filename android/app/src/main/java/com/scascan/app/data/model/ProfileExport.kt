package com.scascan.app.data.model

data class ProfileExport(
    val name: String = "",
    val age: Int = 0,
    val heightCm: Int = 0,
    val weightKg: Float = 0f,
    val isMale: Boolean = true,
    val activityIndex: Int = 2,
    val goalIndex: Int = 1,
    val aiCalorieTarget: Int = 0,
    val aiProteinTarget: Int = 0,
    val aiCarbsTarget: Int = 0,
    val aiFatTarget: Int = 0
)
