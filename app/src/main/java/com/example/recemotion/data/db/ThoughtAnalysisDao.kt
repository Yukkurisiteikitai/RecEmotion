package com.example.recemotion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ThoughtAnalysisDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(analysis: ThoughtAnalysisEntity): Long

    @Query("SELECT * FROM thought_analyses WHERE entry_id = :entryId ORDER BY created_at DESC LIMIT 1")
    suspend fun latestForEntry(entryId: Long): ThoughtAnalysisEntity?
}
