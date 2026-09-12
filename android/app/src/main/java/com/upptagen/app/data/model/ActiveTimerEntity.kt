package com.upptagen.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "active_timer")
data class ActiveTimerEntity(
    @PrimaryKey
    val id: Long = 1,
    val project: String? = null,
    val description: String? = null,
    @ColumnInfo(name = "start_time")
    val startTime: String, // RFC 3339 / ISO 8601 string
    @ColumnInfo(name = "tags_json")
    val tagsJson: String = "[]" // JSON array of tag name strings
)
