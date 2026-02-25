package com.example.recemotion.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 感情タイムラインエントリ。
 * 分析時点・定期的な顔感情スナップショットを記録し、
 * LLM へ感情状態ログとして渡すために利用する。
 */
@Entity(tableName = "emotion_timeline")
data class EmotionTimelineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val emotion: String,        // Rust JNI の current_emotion (e.g. "Happy", "Neutral")
    val stressLevel: Int,       // 1-5 (スライダー値)
    val energyLevel: Int,       // Rust context の energy_level
    val sessionDate: String,    // yyyy-MM-dd
    val trigger: String         // "analysis" | "periodic"
)
