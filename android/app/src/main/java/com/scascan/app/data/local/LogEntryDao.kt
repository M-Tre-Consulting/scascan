package com.scascan.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {
    @Insert
    suspend fun insert(entry: LogEntry): Long

    @Update
    suspend fun update(entry: LogEntry)

    @Delete
    suspend fun delete(entry: LogEntry)

    @Query("SELECT * FROM log_entries WHERE timestamp >= :startOfDay ORDER BY timestamp DESC")
    fun getEntriesForDay(startOfDay: Long): Flow<List<LogEntry>>
}
