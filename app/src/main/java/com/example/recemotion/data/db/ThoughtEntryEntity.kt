package com.example.recemotion.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stored user text and its parsed thought tree.
 */
@Entity(
    tableName = "thought_entries",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = ConversationTopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topic_id"],
            onDelete = androidx.room.ForeignKey.SET_NULL
        )
    ],
    indices = [androidx.room.Index(value = ["topic_id"])]
)
data class ThoughtEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "topic_id") val topicId: Long? = null,
    @ColumnInfo(name = "raw_text") val rawText: String,
    @ColumnInfo(name = "tree_json") val treeJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
