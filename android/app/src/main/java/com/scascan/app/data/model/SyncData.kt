package com.scascan.app.data.model

import com.scascan.app.data.local.LogEntry

data class SyncData(
    val profile: ProfileExport = ProfileExport(),
    val logs: List<LogEntry> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)
