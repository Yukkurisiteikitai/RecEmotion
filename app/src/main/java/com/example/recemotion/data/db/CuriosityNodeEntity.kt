package com.example.recemotion.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "curiosity_nodes",
    foreignKeys = [
        ForeignKey(
            entity = TaskPhaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_phase_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["parent_phase_id"])]
)
data class CuriosityNodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "parent_phase_id") val parentPhaseId: Long,
    @ColumnInfo(name = "question") val question: String,
    @ColumnInfo(name = "relevance") val relevance: Int,
    @ColumnInfo(name = "priority") val priority: Int,
    @ColumnInfo(name = "depth") val depth: Int,
    @ColumnInfo(name = "status") val status: String
)
