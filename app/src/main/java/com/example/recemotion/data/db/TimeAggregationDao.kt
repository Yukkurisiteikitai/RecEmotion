package com.example.recemotion.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeAggregationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TimeAggregationEntity): Long

    @Update
    suspend fun update(entity: TimeAggregationEntity)

    @Delete
    suspend fun delete(entity: TimeAggregationEntity)

    @Query("SELECT * FROM time_aggregations WHERE task_id = :taskId ORDER BY id ASC")
    suspend fun getByTask(taskId: Long): List<TimeAggregationEntity>

    @Query(
        "SELECT p.phase_type AS phaseType, " +
            "COALESCE(SUM(t.total_seconds), 0) AS totalSeconds, " +
            "COALESCE(SUM(t.planned_seconds), 0) AS plannedSeconds " +
            "FROM task_phases p " +
            "LEFT JOIN time_aggregations t ON t.task_id = p.task_id " +
            "WHERE p.task_id = :taskId " +
            "GROUP BY p.phase_type " +
            "ORDER BY MIN(p.phase_order) ASC"
    )
    fun getPhaseBreakdown(taskId: Long): Flow<List<PhaseTimeBreakdown>>
}

data class PhaseTimeBreakdown(
    val phaseType: String,
    val totalSeconds: Long,
    val plannedSeconds: Long
)
