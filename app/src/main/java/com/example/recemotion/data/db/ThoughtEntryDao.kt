package com.example.recemotion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ThoughtEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: ThoughtEntryEntity): Long

    @androidx.room.Update
    suspend fun updateEntry(entry: ThoughtEntryEntity)

    @Query("SELECT * FROM thought_entries WHERE id = :id")
    suspend fun getEntryById(id: Long): ThoughtEntryEntity?

    @Query("SELECT * FROM thought_entries ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestEntry(): ThoughtEntryEntity?

    @Query("SELECT * FROM thought_entries WHERE topic_id = :topicId ORDER BY created_at DESC")
    fun getEntriesByTopic(topicId: Long): Flow<List<ThoughtEntryEntity>>

    @Query("SELECT * FROM thought_entries ORDER BY created_at DESC")
    fun getAllEntries(): Flow<List<ThoughtEntryEntity>>
}
