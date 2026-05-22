package com.scascan.app.data.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
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
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
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

    suspend fun readTodaySteps(): Long {
        val c = client ?: return 0L
        return try {
            val (start, end) = todayRange()
            c.readRecords(ReadRecordsRequest(StepsRecord::class, TimeRangeFilter.between(start, end)))
                .records.sumOf { it.count }
        } catch (e: Exception) {
            Log.e(TAG, "readTodaySteps error: $e")
            0L
        }
    }

    suspend fun readTodayActiveCalories(): Double {
        val c = client ?: return 0.0
        return try {
            val (start, end) = todayRange()
            c.readRecords(
                ReadRecordsRequest(TotalCaloriesBurnedRecord::class, TimeRangeFilter.between(start, end))
            ).records.sumOf { it.energy.inKilocalories }
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
