package com.scascan.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    @Query("SELECT * FROM log_entries WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    fun getEntriesForRange(start: Long, end: Long): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries WHERE timestamp >= :start AND timestamp < :end")
    suspend fun getEntriesForRangeSync(start: Long, end: Long): List<LogEntry>

    @Query("SELECT * FROM log_entries")
    suspend fun getAllEntries(): List<LogEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<LogEntry>)
}
