package com.mtreconsulting.scascan.data.model

import com.mtreconsulting.scascan.data.local.LogEntry

data class SyncData(
    val profile: ProfileExport = ProfileExport(),
    val logs: List<LogEntry> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)
