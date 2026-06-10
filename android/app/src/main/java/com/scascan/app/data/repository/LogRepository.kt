package com.scascan.app.data.repository

import com.scascan.app.data.local.LogEntry
import com.scascan.app.data.local.LogEntryDao
import com.scascan.app.data.local.UserProfileStore
import com.scascan.app.data.model.MacroTargets
import com.scascan.app.data.model.NutritionFacts
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val dao: LogEntryDao,
    private val waterDao: com.scascan.app.data.local.WaterLogDao,
    private val profileStore: UserProfileStore,
    private val healthManager: com.scascan.app.data.health.HealthConnectManager
) {
    fun todayEntries(): Flow<List<LogEntry>> = entriesForDateOffset(0)

    fun entriesForDateOffset(offsetDays: Int): Flow<List<LogEntry>> {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, offsetDays)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val end = start + DAY_MS
        return dao.getEntriesForRange(start, end)
    }

    suspend fun addEntry(facts: NutritionFacts) {
        dao.insert(
            LogEntry(
                foodName = facts.foodName,
                servingSize = facts.servingSize,
                calories = facts.calories,
                protein = facts.protein,
                carbohydrates = facts.carbohydrates,
                fat = facts.fat,
                fiber = facts.fiber,
                sugar = facts.sugar,
                sodium = facts.sodium
            )
        )
        com.scascan.app.ui.widget.SummaryWidgetProvider.triggerUpdate(context)
    }

    suspend fun updateEntry(entry: LogEntry) {
        dao.update(entry)
        com.scascan.app.ui.widget.SummaryWidgetProvider.triggerUpdate(context)
    }

    suspend fun deleteEntry(entry: LogEntry) {
        dao.delete(entry)
        com.scascan.app.ui.widget.SummaryWidgetProvider.triggerUpdate(context)
    }

    fun dailyCalorieTarget(): Int = profileStore.dailyCalorieTarget()
    fun bmr(): Int = profileStore.bmr().toInt()
    fun goalOffset(): Int = profileStore.goalOffset()
    fun macroTargets(): MacroTargets = profileStore.macroTargets()
    fun goalIndex(): Int = profileStore.goalIndex
    fun isAiComputed(): Boolean = profileStore.aiCalorieTarget > 0

    suspend fun getAllEntries(): List<LogEntry> = dao.getAllEntries()

    suspend fun getLiveTarget(): Int {
        val hasHc = healthManager.hasPermissions()
        val bleedthrough = getYesterdayBleedthrough()
        
        val baseTarget = getBaseTarget(hasHc)
        
        val activeKcal = if (hasHc) {
            healthManager.readActiveCalories(0)
        } else {
            0.0
        }
        
        val trendAdjustment = if (hasHc) {
            val weightHist = healthManager.readWeightHistory(28)
            calculateTrendAdjustment(weightHist)
        } else {
            0
        }
        
        return computeFinalTarget(baseTarget, bleedthrough, activeKcal, trendAdjustment)
    }

    fun getBaseTarget(hasHc: Boolean): Int {
        return if (hasHc) {
            if (isAiComputed()) {
                dailyCalorieTarget()
            } else {
                (bmr() * 1.2).toInt() + goalOffset()
            }
        } else {
            dailyCalorieTarget()
        }
    }

    fun computeFinalTarget(base: Int, bleedthrough: Int, active: Double, trend: Int): Int {
        val total = base + bleedthrough + active.roundToInt() + trend
        // Ensure a healthy minimum target (at least 80% of BMR)
        return total.coerceAtLeast((bmr() * 0.8).toInt())
    }

    private fun calculateTrendAdjustment(readings: List<Pair<Long, Double>>): Int {
        if (readings.size < 2) return 0
        val sorted = readings.sortedBy { it.first }
        val daysDiff = (sorted.last().first - sorted.first().first) / 86_400_000.0
        if (daysDiff < 5) return 0

        val weeklyRate = (sorted.last().second - sorted.first().second) / (daysDiff / 7.0)

        return when (goalIndex()) {
            0 -> when {  // lose weight
                weeklyRate < -0.75 -> +200
                weeklyRate > -0.20 -> -200
                else               ->    0
            }
            2 -> when {  // build muscle
                weeklyRate > 0.45 -> -200
                weeklyRate < 0.10 -> +200
                else              ->    0
            }
            else -> 0
        }
    }

    suspend fun getEntriesForRangeSync(start: Long, end: Long): List<LogEntry> = dao.getEntriesForRangeSync(start, end)

    suspend fun getWaterTotalForRangeSync(start: Long, end: Long): Int = waterDao.getTotalForRangeSync(start, end) ?: 0

    suspend fun getYesterdayBleedthrough(): Int {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startMs = cal.timeInMillis
        val endMs = startMs + DAY_MS
        
        // 1. Consumed yesterday
        val yesterdayEntries = dao.getEntriesForRangeSync(startMs, endMs)
        val consumed = yesterdayEntries.sumOf { it.calories }
        
        // 2. Base Allowance yesterday
        // If Health Connect is active, we use a Sedentary (1.2x) base to avoid double-counting activity
        val hasHc = healthManager.hasPermissions()
        val baseTarget = if (hasHc) {
            if (isAiComputed()) {
                dailyCalorieTarget().toDouble()
            } else {
                (bmr() * 1.2) + goalOffset()
            }
        } else {
            dailyCalorieTarget().toDouble()
        }
        
        // 3. Active Burn yesterday
        val startInstant = java.time.Instant.ofEpochMilli(startMs)
        val endInstant = java.time.Instant.ofEpochMilli(endMs)
        val activeYesterday = if (hasHc) {
            healthManager.readActiveCaloriesRange(startInstant, endInstant)
        } else {
            0.0
        }
        
        val totalAllowanceYesterday = baseTarget + activeYesterday
        val rawBalance = totalAllowanceYesterday - consumed
        
        // 4. Smart Logic:
        // - If saved (balance > 0): Use only 80% to account for body efficiency.
        // - If overspent (balance < 0): Carry over 100% to keep you accountable.
        // - Cap at ±500 kcal to keep today's target healthy and achievable.
        val adjustedBalance = if (rawBalance > 0) rawBalance * 0.8 else rawBalance
        
        return adjustedBalance.roundToInt().coerceIn(-500, 500)
    }

    suspend fun upsertEntries(entries: List<LogEntry>) = dao.upsertAll(entries)

    // Water tracking
    fun waterLogsForDateOffset(offsetDays: Int): Flow<List<com.scascan.app.data.local.WaterLog>> {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, offsetDays)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val end = start + DAY_MS
        return waterDao.getLogsForRange(start, end)
    }

    suspend fun addWater(amountMl: Int) {
        waterDao.insert(com.scascan.app.data.local.WaterLog(amountMl = amountMl))
        com.scascan.app.ui.widget.SummaryWidgetProvider.triggerUpdate(context)
    }

    suspend fun deleteWater(entry: com.scascan.app.data.local.WaterLog) {
        waterDao.delete(entry)
        com.scascan.app.ui.widget.SummaryWidgetProvider.triggerUpdate(context)
    }

    suspend fun removeLastWater() {
        waterDao.getLatest()?.let {
            waterDao.delete(it)
            com.scascan.app.ui.widget.SummaryWidgetProvider.triggerUpdate(context)
        }
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
