package com.mtreconsulting.scascan.data.repository

import com.mtreconsulting.scascan.data.local.LogEntry
import com.mtreconsulting.scascan.data.local.LogEntryDao
import com.mtreconsulting.scascan.data.local.UserProfileStore
import com.mtreconsulting.scascan.data.model.MacroTargets
import com.mtreconsulting.scascan.data.model.NutritionFacts
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val dao: LogEntryDao,
    private val waterDao: com.mtreconsulting.scascan.data.local.WaterLogDao,
    private val profileStore: UserProfileStore,
    private val healthManager: com.mtreconsulting.scascan.data.health.HealthConnectManager
) {
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

    suspend fun addEntry(facts: NutritionFacts): LogEntry {
        val entry = LogEntry(
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
        val id = dao.insert(entry)
        com.mtreconsulting.scascan.ui.widget.SummaryWidgetProvider.triggerUpdate(context)
        return entry.copy(id = id)
    }

    suspend fun updateEntry(entry: LogEntry) {
        dao.update(entry)
        com.mtreconsulting.scascan.ui.widget.SummaryWidgetProvider.triggerUpdate(context)
    }

    suspend fun deleteEntry(entry: LogEntry) {
        dao.delete(entry)
        com.mtreconsulting.scascan.ui.widget.SummaryWidgetProvider.triggerUpdate(context)
    }

    fun dailyCalorieTarget(): Int = profileStore.dailyCalorieTarget()
    fun bmr(): Int = profileStore.bmr().toInt()
    fun goalOffset(): Int = profileStore.goalOffset()
    fun macroTargets(): MacroTargets = profileStore.macroTargets()
    fun goalIndex(): Int = profileStore.goalIndex
    fun isAiComputed(): Boolean = profileStore.aiCalorieTarget > 0

    fun syncActiveCalories(kcal: Double) {
        profileStore.lastActiveCalories = kcal.toFloat()
        com.mtreconsulting.scascan.ui.widget.SummaryWidgetProvider.triggerUpdate(context)
    }

    suspend fun getAllEntries(): List<LogEntry> = dao.getAllEntries()

    suspend fun getLiveTarget(): Int {
        val hasHc = healthManager.hasPermissions()
        val bleedthrough = getYesterdayBleedthrough()
        
        val baseTarget = getBaseTarget(hasHc)
        
        var activeKcal = if (hasHc) {
            healthManager.readActiveCalories(0)
        } else {
            0.0
        }
        
        // Background restriction fallback: if live read returns 0, but we have a cached value, use it.
        // This is primarily for the home screen widget which can't always read HC data in the background.
        // Note: HealthConnectManager.readActiveCaloriesRange() now substitutes its own flat
        // "watch not worn" estimate (>= 100 kcal) whenever HC has data but it's near-zero, so this
        // branch is mostly only reachable via a real background-read failure, not a low-activity day.
        if (hasHc && activeKcal <= 0.1 && profileStore.lastActiveCalories > 0) {
            activeKcal = profileStore.lastActiveCalories.toDouble()
        } else if (hasHc && activeKcal > 0.1) {
            // Update cache when we have a fresh reading
            profileStore.lastActiveCalories = activeKcal.toFloat()
        }
        
        val trendAdjustment = if (hasHc) {
            val weightHist = healthManager.readWeightHistory(28)
            calculateTrendAdjustment(weightHist)
        } else {
            0
        }

        return computeFinalTarget(baseTarget, bleedthrough, trendAdjustment)
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

    /**
     * The live/displayed daily target: base + carry-over + weight trend.
     * Deliberately excludes today's measured activity burn — that's settled once, in the
     * evening recap ([getDailyRecap]), as a deduction from intake, so the number the user is
     * aiming at doesn't shift every time Health Connect syncs fresh activity mid-day.
     */
    fun computeFinalTarget(base: Int, bleedthrough: Int, trend: Int): Int {
        val total = base + bleedthrough + trend
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

    private fun dayRangeMs(offsetDays: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, offsetDays)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        return start to (start + DAY_MS)
    }

    /** What the day before [offsetDays] left behind — carries into [offsetDays] as bleedthrough. */
    private suspend fun bleedthroughInto(offsetDays: Int): Int {
        val (startMs, endMs) = dayRangeMs(offsetDays - 1)

        // 1. Consumed on the previous day
        val prevEntries = dao.getEntriesForRangeSync(startMs, endMs)
        val consumed = prevEntries.sumOf { it.calories }

        // 2. Base Allowance on the previous day
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

        // 3. Active Burn on the previous day
        val startInstant = java.time.Instant.ofEpochMilli(startMs)
        val endInstant = java.time.Instant.ofEpochMilli(endMs)
        val activePrevDay = if (hasHc) {
            healthManager.readActiveCaloriesRange(startInstant, endInstant)
        } else {
            0.0
        }

        val totalAllowancePrevDay = baseTarget + activePrevDay
        val rawBalance = totalAllowancePrevDay - consumed

        // 4. Smart Logic:
        // - If saved (balance > 0): Use only 80% to account for body efficiency.
        // - If overspent (balance < 0): Carry over 100% to keep you accountable.
        // - Cap at ±500 kcal to keep the receiving day's target healthy and achievable.
        val adjustedBalance = if (rawBalance > 0) rawBalance * 0.8 else rawBalance

        return adjustedBalance.roundToInt().coerceIn(-500, 500)
    }

    suspend fun getYesterdayBleedthrough(): Int = bleedthroughInto(0)

    enum class RecapVerdict { OVER, UNDER, ON_TARGET, NO_DATA }

    data class DailyRecap(
        val eaten: Int,
        val burned: Int,
        val carryOver: Int,
        val net: Int,
        val target: Int,
        val verdict: RecapVerdict,
        val waterMl: Int,
        val waterTargetMl: Int
    )

    /**
     * Settles the given day once: net = eaten − burned − carryOver, compared against
     * base + weightTrend (no carry-over, no burn — those are already accounted for in net).
     * Mirrors iOS's evening recap ledger (see ios/ARCHITECTURE.md §5.4).
     */
    suspend fun getDailyRecap(offsetDays: Int): DailyRecap {
        val (startMs, endMs) = dayRangeMs(offsetDays)

        val entries = dao.getEntriesForRangeSync(startMs, endMs)
        val eaten = entries.sumOf { it.calories }.roundToInt()
        val water = waterDao.getTotalForRangeSync(startMs, endMs) ?: 0

        val hasHc = healthManager.hasPermissions()
        val carryOver = bleedthroughInto(offsetDays)

        val trend = if (offsetDays == 0 && hasHc) {
            calculateTrendAdjustment(healthManager.readWeightHistory(28))
        } else {
            0
        }
        val target = (getBaseTarget(hasHc) + trend).coerceAtLeast((bmr() * 0.8).toInt())

        val burned = if (hasHc) {
            val startInstant = java.time.Instant.ofEpochMilli(startMs)
            val endInstant = java.time.Instant.ofEpochMilli(endMs)
            healthManager.readActiveCaloriesRange(startInstant, endInstant).roundToInt()
        } else {
            0
        }

        val net = eaten - burned - carryOver
        val verdict = when {
            entries.isEmpty() -> RecapVerdict.NO_DATA
            net > target + 100 -> RecapVerdict.OVER
            net < target * 0.75 -> RecapVerdict.UNDER
            else -> RecapVerdict.ON_TARGET
        }

        return DailyRecap(
            eaten = eaten,
            burned = burned,
            carryOver = carryOver,
            net = net,
            target = target,
            verdict = verdict,
            waterMl = water,
            waterTargetMl = profileStore.waterTargetMl()
        )
    }

    suspend fun upsertEntries(entries: List<LogEntry>) = dao.upsertAll(entries)

    // Water tracking
    fun waterLogsForDateOffset(offsetDays: Int): Flow<List<com.mtreconsulting.scascan.data.local.WaterLog>> {
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
        waterDao.insert(com.mtreconsulting.scascan.data.local.WaterLog(amountMl = amountMl))
        com.mtreconsulting.scascan.ui.widget.SummaryWidgetProvider.triggerUpdate(context)
    }

    suspend fun removeLastWater() {
        waterDao.getLatest()?.let {
            waterDao.delete(it)
            com.mtreconsulting.scascan.ui.widget.SummaryWidgetProvider.triggerUpdate(context)
        }
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
