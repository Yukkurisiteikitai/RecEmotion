package com.example.recemotion.data.repository

import com.example.recemotion.data.db.TaskPhaseDao
import com.example.recemotion.data.db.TaskPhaseEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskPhaseRepository @Inject constructor(
    private val taskPhaseDao: TaskPhaseDao
) {

    suspend fun createPhase(phase: TaskPhaseEntity): Long = taskPhaseDao.insert(phase)

    suspend fun updatePhase(phase: TaskPhaseEntity) = taskPhaseDao.update(phase)

    suspend fun deletePhase(phase: TaskPhaseEntity) = taskPhaseDao.delete(phase)

    fun getPhasesByTask(taskId: Long): Flow<List<TaskPhaseEntity>> =
        taskPhaseDao.getPhasesByTask(taskId)

    suspend fun getCurrentPhase(taskId: Long): TaskPhaseEntity? =
        taskPhaseDao.getCurrentPhase(taskId)

    suspend fun completeCurrentPhase(taskId: Long, endTime: Long): Boolean {
        val current = taskPhaseDao.getCurrentPhase(taskId) ?: return false
        taskPhaseDao.update(current.copy(status = "DONE", endTime = endTime))
        return true
    }
}
