package com.mtreconsulting.scascan.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Every field defaults so Kotlin synthesizes a no-arg constructor — required for Firestore's
// automatic POJO mapping in FirestoreSyncManager (Room itself doesn't care either way).
@Entity(tableName = "log_entries")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val foodName: String = "",
    val servingSize: String = "",
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbohydrates: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val sodium: Double = 0.0
)
