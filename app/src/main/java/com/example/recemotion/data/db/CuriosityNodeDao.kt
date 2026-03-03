package com.example.recemotion.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CuriosityNodeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CuriosityNodeEntity): Long

    @Update
    suspend fun update(entity: CuriosityNodeEntity)

    @Delete
    suspend fun delete(entity: CuriosityNodeEntity)

    @Query(
        "SELECT c.* FROM curiosity_nodes c " +
            "INNER JOIN task_phases p ON c.parent_phase_id = p.id " +
            "WHERE p.task_id = :taskId AND c.status != 'ANSWERED' " +
            "ORDER BY c.priority DESC, c.relevance DESC"
    )
    fun getUnansweredByTask(taskId: Long): Flow<List<CuriosityNodeEntity>>

    @Query("SELECT * FROM curiosity_nodes WHERE relevance >= :minRelevance ORDER BY relevance DESC")
    fun getByRelevance(minRelevance: Int): Flow<List<CuriosityNodeEntity>>
}
