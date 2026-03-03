package com.example.recemotion.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hypotheses",
    foreignKeys = [
        ForeignKey(
            entity = TaskPhaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["phase_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["phase_id"])]
)
data class HypothesisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "phase_id") val phaseId: Long,
    @ColumnInfo(name = "hypothesis") val hypothesis: String,
    @ColumnInfo(name = "expected_outcome") val expectedOutcome: String,
    @ColumnInfo(name = "actual_outcome") val actualOutcome: String?,
    @ColumnInfo(name = "gap_analysis") val gapAnalysis: String?
)
