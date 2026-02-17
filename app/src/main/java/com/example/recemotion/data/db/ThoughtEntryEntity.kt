package com.example.recemotion.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stored user text and its parsed thought tree.
 */
@Entity(tableName = "thought_entries")
data class ThoughtEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "raw_text") val rawText: String,
    @ColumnInfo(name = "tree_json") val treeJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
