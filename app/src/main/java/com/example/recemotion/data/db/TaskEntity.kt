package com.example.recemotion.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "importance") val importance: Int,
    @ColumnInfo(name = "urgency") val urgency: Int,
    @ColumnInfo(name = "scope") val scope: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "target_completion_date") val targetCompletionDate: Long?,
    @ColumnInfo(name = "current_phase") val currentPhase: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "actual_minutes") val actualMinutes: Int = 0
)
