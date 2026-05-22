package com.scascan.app.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var age: Int
        get() = prefs.getInt(KEY_AGE, 0)
        set(v) { prefs.edit().putInt(KEY_AGE, v).commit() }

    /** true = male, false = female */
    var isMale: Boolean
        get() = prefs.getBoolean(KEY_SEX_MALE, true)
        set(v) { prefs.edit().putBoolean(KEY_SEX_MALE, v).commit() }

    var heightCm: Int
        get() = prefs.getInt(KEY_HEIGHT, 0)
        set(v) { prefs.edit().putInt(KEY_HEIGHT, v).commit() }

    var weightKg: Float
        get() = prefs.getFloat(KEY_WEIGHT, 0f)
        set(v) { prefs.edit().putFloat(KEY_WEIGHT, v).commit() }

    /** 0 = sedentary → 4 = extra active */
    var activityIndex: Int
        get() = prefs.getInt(KEY_ACTIVITY, 2)
        set(v) { prefs.edit().putInt(KEY_ACTIVITY, v).commit() }

    /** 0 = lose weight, 1 = maintain, 2 = build muscle */
    var goalIndex: Int
        get() = prefs.getInt(KEY_GOAL, 1)
        set(v) { prefs.edit().putInt(KEY_GOAL, v).commit() }

    /** AI-computed daily calorie target from Gemini. 0 = not computed yet. */
    var aiCalorieTarget: Int
        get() = prefs.getInt(KEY_AI_CALORIES, 0)
        set(v) { prefs.edit().putInt(KEY_AI_CALORIES, v).commit() }

    fun hasProfile(): Boolean = age > 0 && heightCm > 0 && weightKg > 0f

    /** Returns the AI-computed target if available, otherwise falls back to Mifflin-St Jeor TDEE. */
    fun dailyCalorieTarget(): Int {
        if (aiCalorieTarget > 0) return aiCalorieTarget
        if (!hasProfile()) return DEFAULT_CALORIES
        val bmr = if (isMale) {
            10.0 * weightKg + 6.25 * heightCm - 5.0 * age + 5.0
        } else {
            10.0 * weightKg + 6.25 * heightCm - 5.0 * age - 161.0
        }
        return (bmr * ACTIVITY_MULTIPLIERS[activityIndex.coerceIn(0, 4)]).toInt()
    }

    companion object {
        private const val PREFS_NAME   = "scascan_profile"
        private const val KEY_AGE      = "age"
        private const val KEY_SEX_MALE = "sex_male"
        private const val KEY_HEIGHT   = "height_cm"
        private const val KEY_WEIGHT   = "weight_kg"
        private const val KEY_ACTIVITY = "activity_idx"
        private const val KEY_GOAL     = "goal_idx"
        private const val KEY_AI_CALORIES = "ai_calories"
        const val DEFAULT_CALORIES = 2_000
        val ACTIVITY_MULTIPLIERS = doubleArrayOf(1.2, 1.375, 1.55, 1.725, 1.9)
        val GOAL_LABELS = arrayOf("Lose weight", "Maintain weight", "Build muscle")
    }
}
