package com.scascan.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterLogDao {
    @Insert
    suspend fun insert(entry: WaterLog): Long

    @Delete
    suspend fun delete(entry: WaterLog)

    @Query("SELECT * FROM water_logs WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    fun getLogsForRange(start: Long, end: Long): Flow<List<WaterLog>>

    @Query("SELECT SUM(amountMl) FROM water_logs WHERE timestamp >= :start AND timestamp < :end")
    suspend fun getTotalForRangeSync(start: Long, end: Long): Int?
    
    @Query("SELECT * FROM water_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): WaterLog?
}
