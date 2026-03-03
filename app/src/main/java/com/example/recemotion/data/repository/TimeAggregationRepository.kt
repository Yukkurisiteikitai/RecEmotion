package com.example.recemotion.data.repository

import com.example.recemotion.data.db.PhaseTimeBreakdown
import com.example.recemotion.data.db.TimeAggregationDao
import com.example.recemotion.data.db.TimeAggregationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeAggregationRepository @Inject constructor(
    private val timeAggregationDao: TimeAggregationDao
) {

    suspend fun createAggregation(entity: TimeAggregationEntity): Long = timeAggregationDao.insert(entity)

    suspend fun updateAggregation(entity: TimeAggregationEntity) = timeAggregationDao.update(entity)

    suspend fun deleteAggregation(entity: TimeAggregationEntity) = timeAggregationDao.delete(entity)

    suspend fun getByTask(taskId: Long): List<TimeAggregationEntity> =
        timeAggregationDao.getByTask(taskId)

    fun getByTaskFlow(taskId: Long): Flow<List<TimeAggregationEntity>> = flow {
        emit(timeAggregationDao.getByTask(taskId))
    }

    fun getPhaseBreakdown(taskId: Long): Flow<List<PhaseTimeBreakdown>> =
        timeAggregationDao.getPhaseBreakdown(taskId)
}
