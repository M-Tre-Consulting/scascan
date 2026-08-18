package com.mtreconsulting.scascan.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [LogEntry::class, WaterLog::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun logEntryDao(): LogEntryDao
    abstract fun waterLogDao(): WaterLogDao
}
