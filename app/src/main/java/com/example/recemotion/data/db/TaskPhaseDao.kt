package com.example.recemotion.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskPhaseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TaskPhaseEntity): Long

    @Update
    suspend fun update(entity: TaskPhaseEntity)

    @Delete
    suspend fun delete(entity: TaskPhaseEntity)

    @Query("SELECT * FROM task_phases WHERE task_id = :taskId ORDER BY phase_order ASC")
    fun getPhasesByTask(taskId: Long): Flow<List<TaskPhaseEntity>>

    @Query(
        "SELECT * FROM task_phases " +
            "WHERE task_id = :taskId AND status = 'IN_PROGRESS' " +
            "ORDER BY phase_order ASC LIMIT 1"
    )
    suspend fun getCurrentPhase(taskId: Long): TaskPhaseEntity?
}
