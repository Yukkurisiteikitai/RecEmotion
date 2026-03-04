package com.example.recemotion.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_phases",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["task_id"])]
)
data class TaskPhaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "task_id") val taskId: Long,
    @ColumnInfo(name = "phase_type") val phaseType: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "start_time") val startTime: Long?,
    @ColumnInfo(name = "end_time") val endTime: Long?,
    @ColumnInfo(name = "notes") val notes: String?,
    @ColumnInfo(name = "phase_order") val order: Int
)
