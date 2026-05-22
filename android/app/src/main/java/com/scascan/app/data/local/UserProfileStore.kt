package com.scascan.app.data.local

import android.content.Context
import com.scascan.app.data.model.MacroTargets
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var name: String
        get() = prefs?.getString(KEY_NAME, "") ?: ""
        set(v) { prefs?.edit()?.putString(KEY_NAME, v)?.apply() }

    var age: Int
        get() = prefs.getInt(KEY_AGE, 0)
        set(v) { prefs.edit().putInt(KEY_AGE, v).commit() }

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

    /** AI-computed daily calorie target. 0 = not computed yet. */
    var aiCalorieTarget: Int
        get() = prefs.getInt(KEY_AI_CALORIES, 0)
        set(v) { prefs.edit().putInt(KEY_AI_CALORIES, v).commit() }

    var aiProteinTarget: Int
        get() = prefs.getInt(KEY_AI_PROTEIN, 0)
        set(v) { prefs.edit().putInt(KEY_AI_PROTEIN, v).commit() }

    var aiCarbsTarget: Int
        get() = prefs.getInt(KEY_AI_CARBS, 0)
        set(v) { prefs.edit().putInt(KEY_AI_CARBS, v).commit() }

    var aiFatTarget: Int
        get() = prefs.getInt(KEY_AI_FAT, 0)
        set(v) { prefs.edit().putInt(KEY_AI_FAT, v).commit() }

    var syncEmail: String
        get() = prefs.getString(KEY_SYNC_EMAIL, "") ?: ""
        set(v) { prefs.edit().putString(KEY_SYNC_EMAIL, v).commit() }

    fun hasProfile(): Boolean = age > 0 && heightCm > 0 && weightKg > 0f

    /** Returns the AI-computed target if available, otherwise falls back to Mifflin-St Jeor TDEE with goal adjustment. */
    fun dailyCalorieTarget(): Int {
        if (aiCalorieTarget > 0) return aiCalorieTarget
        if (!hasProfile()) return DEFAULT_CALORIES
        
        val tdee = bmr() * ACTIVITY_MULTIPLIERS[activityIndex.coerceIn(0, 4)]
        val offset = when (goalIndex.coerceIn(0, 2)) {
            0 -> -500 // Lose weight: ~0.5kg/week deficit
            2 -> 300  // Build muscle: surplus
            else -> 0 // Maintain
        }
        
        return (tdee + offset).toInt()
    }

    fun bmr(): Double {
        if (!hasProfile()) return 1700.0
        return if (isMale) {
            10.0 * weightKg + 6.25 * heightCm - 5.0 * age + 5.0
        } else {
            10.0 * weightKg + 6.25 * heightCm - 5.0 * age - 161.0
        }
    }

    /**
     * Returns macro targets in grams. Uses AI-computed values when available;
     * falls back to goal-based ratios derived from the calorie target.
     *
     * Ratios (protein/carbs/fat):
     *   Lose weight  → 30 % / 35 % / 35 %  (high protein to preserve muscle)
     *   Maintain     → 20 % / 50 % / 30 %  (standard balanced)
     *   Build muscle → 30 % / 45 % / 25 %  (high carbs + protein for hypertrophy)
     */
    fun macroTargets(): MacroTargets {
        if (aiProteinTarget > 0) {
            return MacroTargets(aiProteinTarget, aiCarbsTarget, aiFatTarget)
        }
        val cal = dailyCalorieTarget()
        return when (goalIndex.coerceIn(0, 2)) {
            0 -> MacroTargets(
                proteinG = (cal * 0.30 / 4).toInt(),
                carbsG   = (cal * 0.35 / 4).toInt(),
                fatG     = (cal * 0.35 / 9).toInt()
            )
            2 -> MacroTargets(
                proteinG = (cal * 0.30 / 4).toInt(),
                carbsG   = (cal * 0.45 / 4).toInt(),
                fatG     = (cal * 0.25 / 9).toInt()
            )
            else -> MacroTargets(
                proteinG = (cal * 0.20 / 4).toInt(),
                carbsG   = (cal * 0.50 / 4).toInt(),
                fatG     = (cal * 0.30 / 9).toInt()
            )
        }
    }

    fun goalOffset(): Int {
        return when (goalIndex.coerceIn(0, 2)) {
            0 -> -500
            2 -> 300
            else -> 0
        }
    }

    companion object {
        private const val PREFS_NAME      = "scascan_profile"
        private const val KEY_AGE         = "age"
        private const val KEY_SEX_MALE    = "sex_male"
        private const val KEY_HEIGHT      = "height_cm"
        private const val KEY_WEIGHT      = "weight_kg"
        private const val KEY_ACTIVITY    = "activity_idx"
        private const val KEY_GOAL        = "goal_idx"
        private const val KEY_AI_CALORIES = "ai_calories"
        private const val KEY_AI_PROTEIN  = "ai_protein"
        private const val KEY_AI_CARBS    = "ai_carbs"
        private const val KEY_AI_FAT      = "ai_fat"
        private const val KEY_NAME        = "user_name"
        private const val KEY_SYNC_EMAIL   = "sync_email"
        const val DEFAULT_CALORIES = 2_000
        val ACTIVITY_MULTIPLIERS = doubleArrayOf(1.2, 1.375, 1.55, 1.725, 1.9)
        val GOAL_LABELS = arrayOf("Lose weight", "Maintain weight", "Build muscle")
    }
}
