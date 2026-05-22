package com.scascan.app.data.repository

import com.scascan.app.data.local.LogEntry
import com.scascan.app.data.local.LogEntryDao
import com.scascan.app.data.local.UserProfileStore
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
    fun todayEntries(): Flow<List<LogEntry>> = dao.getEntriesForDay(startOfToday())

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

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
