package com.example.recemotion.data.repository

import com.example.recemotion.data.db.EmotionTimelineDao
import com.example.recemotion.data.db.EmotionTimelineEntity
import com.example.recemotion.domain.model.EmotionEntry
import com.example.recemotion.domain.repository.EmotionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class EmotionRepositoryImpl @Inject constructor(
    private val dao: EmotionTimelineDao
) : EmotionRepository {

    override suspend fun insert(
        emotion: String,
        stressLevel: Int,
        energyLevel: Int,
        sessionDate: String,
        trigger: String
    ) {
        dao.insert(
            EmotionTimelineEntity(
                emotion = emotion,
                stressLevel = stressLevel,
                energyLevel = energyLevel,
                sessionDate = sessionDate,
                trigger = trigger
            )
        )
    }

    override suspend fun getRecent(limit: Int): List<EmotionEntry> =
        dao.getRecent(limit).map { it.toDomain() }

    override fun getByDate(date: String): Flow<List<EmotionEntry>> =
        dao.getByDate(date).map { list -> list.map { it.toDomain() } }

    override suspend fun getAroundTime(startTime: Long, endTime: Long): List<EmotionEntry> =
        dao.getAroundTime(startTime, endTime).map { it.toDomain() }

    private fun EmotionTimelineEntity.toDomain() = EmotionEntry(
        id = id,
        timestamp = timestamp,
        emotion = emotion,
        stressLevel = stressLevel,
        energyLevel = energyLevel,
        sessionDate = sessionDate,
        trigger = trigger
    )
}
