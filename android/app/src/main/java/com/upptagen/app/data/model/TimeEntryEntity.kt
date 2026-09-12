package com.upptagen.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class TimeEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val project: String? = null,
    val description: String? = null,
    @ColumnInfo(name = "start_time")
    val startTime: String, // ISO-8601 / RFC 3339 string (e.g. 2025-01-01T10:00:00Z)
    @ColumnInfo(name = "end_time")
    val endTime: String,
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Long
)
