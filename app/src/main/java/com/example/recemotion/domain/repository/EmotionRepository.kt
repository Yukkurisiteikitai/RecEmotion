package com.example.recemotion.domain.repository

import com.example.recemotion.domain.model.EmotionEntry
import kotlinx.coroutines.flow.Flow

interface EmotionRepository {
    suspend fun insert(emotion: String, stressLevel: Int, energyLevel: Int, sessionDate: String, trigger: String)
    suspend fun getRecent(limit: Int): List<EmotionEntry>
    fun getByDate(date: String): Flow<List<EmotionEntry>>
    suspend fun getAroundTime(startTime: Long, endTime: Long): List<EmotionEntry>
}
