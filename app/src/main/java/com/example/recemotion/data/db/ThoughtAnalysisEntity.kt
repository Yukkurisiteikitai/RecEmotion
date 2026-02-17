package com.example.recemotion.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stored LLM analysis result for a thought entry.
 */
@Entity(
    tableName = "thought_analyses",
    foreignKeys = [
        ForeignKey(
            entity = ThoughtEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["entry_id"])]
)
data class ThoughtAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entry_id") val entryId: Long,
    @ColumnInfo(name = "analysis_json") val analysisJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
