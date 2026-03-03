package com.example.recemotion.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HypothesisDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HypothesisEntity): Long

    @Update
    suspend fun update(entity: HypothesisEntity)

    @Delete
    suspend fun delete(entity: HypothesisEntity)

    @Query("SELECT * FROM hypotheses WHERE phase_id = :phaseId ORDER BY id DESC")
    fun getByPhase(phaseId: Long): Flow<List<HypothesisEntity>>

    @Query(
        "SELECT h.* FROM hypotheses h " +
            "INNER JOIN task_phases p ON h.phase_id = p.id " +
            "WHERE p.task_id = :taskId " +
            "ORDER BY p.phase_order ASC, h.id DESC"
    )
    fun getByTask(taskId: Long): Flow<List<HypothesisEntity>>
}
