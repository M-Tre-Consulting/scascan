package com.scascan.app.data.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.scascan.app.data.local.UserProfileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val profileStore: UserProfileStore
) {
    companion object {
        private const val TAG = "HealthConnectManager"

        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(HeightRecord::class)
        )
    }

    val isAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient?
        get() = if (isAvailable) HealthConnectClient.getOrCreate(context) else null

    suspend fun revokePermissions() {
        val c = client ?: return
        try {
            c.permissionController.revokeAllPermissions()
        } catch (e: Exception) {
            Log.e(TAG, "revokePermissions error: $e")
        }
    }

    suspend fun hasPermissions(): Boolean {
        val c = client ?: return false
        return try {
            c.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
        } catch (e: Exception) {
            Log.e(TAG, "hasPermissions error: $e")
            false
        }
    }

    suspend fun readSteps(offset: Int): Long {
        val c = client ?: return 0L
        return try {
            val (start, end) = dateRange(offset)
            val response = c.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            response[StepsRecord.COUNT_TOTAL] ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "readSteps error: $e")
            0L
        }
    }

    suspend fun readActiveCalories(offset: Int): Double {
        val (start, end) = dateRange(offset)
        return readActiveCaloriesRange(start, end)
    }

    private fun dateRange(offset: Int): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone).plusDays(offset.toLong())
        val start = day.atStartOfDay(zone).toInstant()
        val end = if (offset == 0) Instant.now() else day.plusDays(1).atStartOfDay(zone).toInstant()
        return start to end
    }

    suspend fun readActiveCaloriesRange(start: Instant, end: Instant): Double {
        val c = client ?: return 0.0
        return try {
            // 1. Try ActiveCaloriesBurnedRecord first (Most accurate for "active" burn)
            val activeResponse = c.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            val activeKcal = activeResponse[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
            
            Log.d(TAG, "Active Kcal from record: $activeKcal")
            // If we have meaningful active calories, return them
            if (activeKcal > 1.0) return activeKcal

            // 2. Fallback: (TotalCaloriesBurnedRecord - BMR)
            // This is necessary because some platforms record everything as "Total"
            val totalResponse = c.aggregate(
                AggregateRequest(
                    metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            val totalKcal = totalResponse[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
            
            if (totalKcal <= 0.0) return 0.0

            // Get BMR for the same period
            val bmrResponse = c.aggregate(
                AggregateRequest(
                    metrics = setOf(BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            var bmrKcal = bmrResponse[BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL]?.inKilocalories ?: 0.0
            
            // If BMR is missing in HC (common), estimate it from profile to avoid huge numbers
            if (bmrKcal <= 0.0) {
                val dailyBmr = estimateDailyBmr()
                val minutesPassed = Duration.between(start, end).toMinutes()
                bmrKcal = (dailyBmr / 1440.0) * minutesPassed
            }
            
            Log.d(TAG, "Total Kcal: $totalKcal, BMR Kcal: $bmrKcal, Diff: ${totalKcal - bmrKcal}")
            (totalKcal - bmrKcal).coerceAtLeast(0.0)
        } catch (e: Exception) {
            Log.e(TAG, "readActiveCaloriesRange error: $e")
            0.0
        }
    }

    private fun estimateDailyBmr(): Double = profileStore.bmr()

    /**
     * Returns (timestampMs, weightKg) pairs in ascending order for the past [pastDays] days.
     * Used by the adaptive target engine to compute weight-change trend.
     */
    suspend fun readWeightHistory(pastDays: Int = 28): List<Pair<Long, Double>> {
        val c = client ?: return emptyList()
        return try {
            val end = Instant.now()
            val start = end.minus(pastDays.toLong(), ChronoUnit.DAYS)
            c.readRecords(
                ReadRecordsRequest(
                    WeightRecord::class,
                    TimeRangeFilter.between(start, end),
                    ascendingOrder = true
                )
            ).records.map { it.time.toEpochMilli() to it.weight.inKilograms }
        } catch (e: Exception) {
            Log.e(TAG, "readWeightHistory error: $e")
            emptyList()
        }
    }

    /** Most recent weight within the last 30 days, or null. */
    suspend fun readLatestWeightKg(): Double? {
        val c = client ?: return null
        return try {
            val end = Instant.now()
            val start = end.minus(30, ChronoUnit.DAYS)
            c.readRecords(
                ReadRecordsRequest(
                    WeightRecord::class,
                    TimeRangeFilter.between(start, end),
                    ascendingOrder = false
                )
            ).records.firstOrNull()?.weight?.inKilograms
        } catch (e: Exception) {
            Log.e(TAG, "readLatestWeightKg error: $e")
            null
        }
    }

    suspend fun readLatestHeightCm(): Double? {
        val c = client ?: return null
        return try {
            val end = Instant.now()
            val start = end.minus(365, ChronoUnit.DAYS) // Height doesn't change often
            c.readRecords(
                ReadRecordsRequest(
                    HeightRecord::class,
                    TimeRangeFilter.between(start, end),
                    ascendingOrder = false
                )
            ).records.firstOrNull()?.height?.inMeters?.times(100.0)
        } catch (e: Exception) {
            Log.e(TAG, "readLatestHeight error: $e")
            null
        }
    }
}
