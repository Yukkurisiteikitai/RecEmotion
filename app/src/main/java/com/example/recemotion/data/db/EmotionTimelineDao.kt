package com.example.recemotion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EmotionTimelineDao {

    @Insert
    suspend fun insert(entity: EmotionTimelineEntity)

    /** 指定日のエントリを時系列順に取得（Flow でリアルタイム監視） */
    @Query("SELECT * FROM emotion_timeline WHERE sessionDate = :date ORDER BY timestamp ASC")
    fun getByDate(date: String): Flow<List<EmotionTimelineEntity>>

    /** 指定タイムスタンプ周辺 (±5分) のエントリを取得 */
    @Query(
        "SELECT * FROM emotion_timeline " +
        "WHERE timestamp BETWEEN :startTime AND :endTime " +
        "ORDER BY timestamp ASC"
    )
    suspend fun getAroundTime(startTime: Long, endTime: Long): List<EmotionTimelineEntity>

    /** 直近 N 件を取得（プロンプト構築用） */
    @Query("SELECT * FROM emotion_timeline ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<EmotionTimelineEntity>
}
