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
    private val profileStore: UserProfileStore
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
    fun macroTargets(): MacroTargets = profileStore.macroTargets()
    fun goalIndex(): Int = profileStore.goalIndex
    fun isAiComputed(): Boolean = profileStore.aiCalorieTarget > 0

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
