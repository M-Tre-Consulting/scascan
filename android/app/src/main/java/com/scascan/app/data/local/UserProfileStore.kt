package com.scascan.app.data.local

import android.content.Context
import com.scascan.app.data.model.MacroTargets
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class UserProfileStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var name: String
        get() = prefs?.getString(KEY_NAME, "") ?: ""
        set(v) { prefs.edit(commit = true) { putString(KEY_NAME, v)} }

    var age: Int
        get() = prefs.getInt(KEY_AGE, 0)
        set(v) { prefs.edit(commit = true) { putInt(KEY_AGE, v) } }

    var isMale: Boolean
        get() = prefs.getBoolean(KEY_SEX_MALE, true)
        set(v) { prefs.edit(commit = true) { putBoolean(KEY_SEX_MALE, v) } }

    var heightCm: Int
        get() = prefs.getInt(KEY_HEIGHT, 0)
        set(v) { prefs.edit(commit = true) { putInt(KEY_HEIGHT, v) } }

    var weightKg: Float
        get() = prefs.getFloat(KEY_WEIGHT, 0f)
        set(v) { prefs.edit(commit = true) { putFloat(KEY_WEIGHT, v) } }

    /** 0 = sedentary → 4 = extra active */
    var activityIndex: Int
        get() = prefs.getInt(KEY_ACTIVITY, 2)
        set(v) { prefs.edit(commit = true) { putInt(KEY_ACTIVITY, v) } }

    /** 0 = lose weight, 1 = maintain, 2 = build muscle */
    var goalIndex: Int
        get() = prefs.getInt(KEY_GOAL, 1)
        set(v) { prefs.edit(commit = true) { putInt(KEY_GOAL, v) } }

    /** AI-computed daily calorie target. 0 = not computed yet. */
    var aiCalorieTarget: Int
        get() = prefs.getInt(KEY_AI_CALORIES, 0)
        set(v) { prefs.edit(commit = true) { putInt(KEY_AI_CALORIES, v) } }

    var aiProteinTarget: Int
        get() = prefs.getInt(KEY_AI_PROTEIN, 0)
        set(v) { prefs.edit(commit = true) { putInt(KEY_AI_PROTEIN, v) } }

    var aiCarbsTarget: Int
        get() = prefs.getInt(KEY_AI_CARBS, 0)
        set(v) { prefs.edit(commit = true) { putInt(KEY_AI_CARBS, v) } }

    var aiFatTarget: Int
        get() = prefs.getInt(KEY_AI_FAT, 0)
        set(v) { prefs.edit(commit = true) { putInt(KEY_AI_FAT, v) } }

    var syncEmail: String
        get() = prefs.getString(KEY_SYNC_EMAIL, "") ?: ""
        set(v) { prefs.edit(commit = true) { putString(KEY_SYNC_EMAIL, v) } }

    var waterRemindersEnabled: Boolean
        get() = prefs.getBoolean(KEY_WATER_REMINDERS, false)
        set(v) { prefs.edit(commit = true) { putBoolean(KEY_WATER_REMINDERS, v) } }

    /** Cached active calories from Health Connect to support Widget background updates. */
    var lastActiveCalories: Float
        get() = prefs.getFloat(KEY_LAST_ACTIVE_CALORIES, 0f)
        set(v) { prefs.edit(commit = true) { putFloat(KEY_LAST_ACTIVE_CALORIES, v) } }

    /** Flat kcal estimate substituted when a whole day's Health Connect active-energy read
     * looks like a missing/unworn tracker rather than genuine inactivity. User-configurable
     * in Settings. See [com.scascan.app.data.health.HealthConnectManager]. */
    var activeCalorieFallbackKcal: Int
        get() = prefs.getInt(KEY_ACTIVE_FALLBACK, DEFAULT_ACTIVE_FALLBACK)
        set(v) { prefs.edit(commit = true) { putInt(KEY_ACTIVE_FALLBACK, v) } }

    fun hasProfile(): Boolean = age > 0 && heightCm > 0 && weightKg > 0f

    /** Returns the AI-computed target if available, otherwise falls back to Mifflin-St Jeor TDEE with goal adjustment. */
    fun dailyCalorieTarget(): Int {
        if (aiCalorieTarget > 0) return aiCalorieTarget
        if (!hasProfile()) return DEFAULT_CALORIES
        
        // Refined base multipliers for TDEE.
        // We use slightly lower values because modern trackers (Health Connect) add "Active" calories on top.
        val baseMultiplier = when (activityIndex.coerceIn(0, 4)) {
            0 -> 1.1   // Sedentary (Minimal movement)
            1 -> 1.2   // Lightly active (Office job + some walking)
            2 -> 1.3   // Moderately active (Construction/Retail or daily exercise)
            3 -> 1.45  // Very active (Heavy manual labor or 2h+ training)
            4 -> 1.6   // Extra active (Athlete/Elite level)
            else -> 1.2
        }
        
        val tdee = bmr() * baseMultiplier
        val offset = when (goalIndex.coerceIn(0, 2)) {
            0 -> -500 // Lose weight: ~0.5kg/week deficit
            2 -> 250  // Build muscle: surplus
            else -> 0 // Maintain
        }
        
        return (tdee + offset).toInt()
    }

    fun bmr(): Double {
        if (!hasProfile()) return 1700.0
        // Mifflin-St Jeor Equation (Standard industry formula)
        // For a 28y male, 180cm, 85kg -> (10*85) + (6.25*180) - (5*28) + 5 = 1840 kcal BMR
        // TDEE (Moderate 1.3) = 2392. Deficit (-500) = 1892 kcal.
        return if (isMale) {
            (10.0 * weightKg) + (6.25 * heightCm) - (5.0 * age) + 5.0
        } else {
            (10.0 * weightKg) + (6.25 * heightCm) - (5.0 * age) - 161.0
        }
    }

    /**
     * Returns macro targets in grams. Uses AI-computed values when available;
     * falls back to goal-based ratios derived from the calorie target.
     *
     * Ratios (protein/carbs/fat):
     *   Lose weight  → 40 % / 30 % / 30 %  (higher protein to preserve muscle)
     *   Maintain     → 30 % / 40 % / 30 %  (balanced)
     *   Build muscle → 30 % / 45 % / 25 %  (high carbs + protein for hypertrophy)
     */
    fun macroTargets(): MacroTargets {
        if (aiProteinTarget > 0) {
            return MacroTargets(aiProteinTarget, aiCarbsTarget, aiFatTarget)
        }
        val cal = dailyCalorieTarget()
        return when (goalIndex.coerceIn(0, 2)) {
            0 -> MacroTargets(
                proteinG = (cal * 0.40 / 4).toInt(),
                carbsG   = (cal * 0.30 / 4).toInt(),
                fatG     = (cal * 0.30 / 9).toInt()
            )
            2 -> MacroTargets(
                proteinG = (cal * 0.30 / 4).toInt(),
                carbsG   = (cal * 0.45 / 4).toInt(),
                fatG     = (cal * 0.25 / 9).toInt()
            )
            else -> MacroTargets(
                proteinG = (cal * 0.30 / 4).toInt(),
                carbsG   = (cal * 0.40 / 4).toInt(),
                fatG     = (cal * 0.30 / 9).toInt()
            )
        }
    }

    fun goalOffset(): Int {
        return when (goalIndex.coerceIn(0, 2)) {
            0 -> -500
            2 -> 250
            else -> 0
        }
    }

    /** ~35 ml per kg of body weight, rounded to 100 ml, clamped 1500–4000, 2000 when weight is unknown. */
    fun waterTargetMl(): Int {
        if (weightKg <= 0f) return 2000
        val raw = weightKg * 35.0
        return (Math.round(raw / 100.0) * 100).toInt().coerceIn(1500, 4000)
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
        private const val KEY_WATER_REMINDERS = "water_reminders"
        private const val KEY_LAST_ACTIVE_CALORIES = "last_active_kcal"
        private const val KEY_ACTIVE_FALLBACK = "active_calorie_fallback"
        const val DEFAULT_CALORIES = 2_000
        const val DEFAULT_ACTIVE_FALLBACK = 500
    }
}
