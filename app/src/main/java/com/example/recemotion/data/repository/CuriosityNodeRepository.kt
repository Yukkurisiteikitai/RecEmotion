package com.example.recemotion.data.repository

import com.example.recemotion.data.db.CuriosityNodeDao
import com.example.recemotion.data.db.CuriosityNodeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CuriosityNodeRepository @Inject constructor(
    private val curiosityNodeDao: CuriosityNodeDao
) {

    suspend fun createNode(entity: CuriosityNodeEntity): Long = curiosityNodeDao.insert(entity)

    suspend fun updateNode(entity: CuriosityNodeEntity) = curiosityNodeDao.update(entity)

    suspend fun deleteNode(entity: CuriosityNodeEntity) = curiosityNodeDao.delete(entity)

    fun getUnansweredByTask(taskId: Long): Flow<List<CuriosityNodeEntity>> =
        curiosityNodeDao.getUnansweredByTask(taskId)

    fun getByRelevance(minRelevance: Int): Flow<List<CuriosityNodeEntity>> =
        curiosityNodeDao.getByRelevance(minRelevance)

    fun getTopRelevanceUnansweredByTask(taskId: Long): Flow<CuriosityNodeEntity?> =
        curiosityNodeDao.getUnansweredByTask(taskId).map { list -> list.maxByOrNull { it.relevance } }
}
