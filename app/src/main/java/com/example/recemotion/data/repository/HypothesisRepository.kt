package com.example.recemotion.data.repository

import com.example.recemotion.data.db.HypothesisDao
import com.example.recemotion.data.db.HypothesisEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HypothesisRepository @Inject constructor(
    private val hypothesisDao: HypothesisDao
) {

    suspend fun createHypothesis(entity: HypothesisEntity): Long = hypothesisDao.insert(entity)

    suspend fun updateHypothesis(entity: HypothesisEntity) = hypothesisDao.update(entity)

    suspend fun deleteHypothesis(entity: HypothesisEntity) = hypothesisDao.delete(entity)

    fun getByPhase(phaseId: Long): Flow<List<HypothesisEntity>> = hypothesisDao.getByPhase(phaseId)

    fun getByTask(taskId: Long): Flow<List<HypothesisEntity>> = hypothesisDao.getByTask(taskId)

    fun getLatestByTask(taskId: Long): Flow<HypothesisEntity?> =
        hypothesisDao.getByTask(taskId).map { list -> list.firstOrNull() }
}
