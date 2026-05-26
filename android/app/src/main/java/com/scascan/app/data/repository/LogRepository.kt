package com.scascan.app.data.repository

import com.scascan.app.data.local.LogEntry
import com.scascan.app.data.local.LogEntryDao
import com.scascan.app.data.local.UserProfileStore
import com.scascan.app.data.model.MacroTargets
import com.scascan.app.data.model.NutritionFacts
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepository @Inject constructor(
    private val dao: LogEntryDao,
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

    suspend fun addEntry(facts: NutritionFacts) = dao.insert(
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

    suspend fun updateEntry(entry: LogEntry) = dao.update(entry)

    suspend fun deleteEntry(entry: LogEntry) = dao.delete(entry)

    fun dailyCalorieTarget(): Int = profileStore.dailyCalorieTarget()
    fun bmr(): Int = profileStore.bmr().toInt()
    fun goalOffset(): Int = profileStore.goalOffset()
    fun macroTargets(): MacroTargets = profileStore.macroTargets()
    fun goalIndex(): Int = profileStore.goalIndex
    fun isAiComputed(): Boolean = profileStore.aiCalorieTarget > 0

    suspend fun getAllEntries(): List<LogEntry> = dao.getAllEntries()

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
        val consumed = yesterdayEntries.sumOf { it.calories.toDouble() }
        
        // 2. Base Allowance yesterday
        // If Health Connect is active, we use a Sedentary (1.2x) base to avoid double-counting activity
        val hasHc = healthManager.hasPermissions()
        val baseTarget = if (hasHc) {
            (bmr() * 1.2) + goalOffset()
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
        
        return adjustedBalance.toInt().coerceIn(-500, 500)
    }

    suspend fun upsertEntries(entries: List<LogEntry>) = dao.upsertAll(entries)

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
