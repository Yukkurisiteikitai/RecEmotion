package com.example.recemotion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ThoughtEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ThoughtEntryEntity): Long

    @Query("SELECT * FROM thought_entries ORDER BY created_at DESC LIMIT 1")
    suspend fun latest(): ThoughtEntryEntity?
}
