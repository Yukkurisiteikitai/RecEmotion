package com.example.recemotion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationTopicDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopic(topic: ConversationTopicEntity): Long

    @Update
    suspend fun updateTopic(topic: ConversationTopicEntity)

    @Query("SELECT * FROM conversation_topics WHERE is_resolved = 0 ORDER BY updated_at DESC LIMIT 1")
    suspend fun getActiveTopic(): ConversationTopicEntity?

    @Query("SELECT * FROM conversation_topics ORDER BY updated_at DESC")
    fun getAllTopics(): Flow<List<ConversationTopicEntity>>

    @Query("SELECT * FROM conversation_topics WHERE id = :id")
    suspend fun getTopicById(id: Long): ConversationTopicEntity?
}
