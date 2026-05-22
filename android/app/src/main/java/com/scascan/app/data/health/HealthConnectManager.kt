package com.scascan.app.data.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "HealthConnectManager"

        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class)
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

    /**
     * Returns today's total step count, deduplicated via the aggregate API
     * so multiple data sources (Fitbit, Samsung Health, etc.) don't double-count.
     */
    suspend fun readTodaySteps(): Long {
        val c = client ?: return 0L
        return try {
            val (start, end) = todayRange()
            val response = c.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            response[StepsRecord.COUNT_TOTAL] ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "readTodaySteps error: $e")
            0L
        }
    }

    /**
     * Returns today's ACTIVE calories burned (exercise + movement above baseline),
     * deduplicated via the aggregate API.
     *
     * Using ActiveCaloriesBurnedRecord — not TotalCaloriesBurnedRecord — to avoid
     * double-counting the BMR that is already factored into the user's daily target.
     */
    suspend fun readTodayActiveCalories(): Double {
        val c = client ?: return 0.0
        return try {
            val (start, end) = todayRange()
            val response = c.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
        } catch (e: Exception) {
            Log.e(TAG, "readTodayActiveCalories error: $e")
            0.0
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

    private fun todayRange(): Pair<Instant, Instant> {
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        return start to start.plus(1, ChronoUnit.DAYS)
    }
}
