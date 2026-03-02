package com.example.recemotion.domain.model

data class EmotionEntry(
    val id: Long,
    val timestamp: Long,
    val emotion: String,
    val stressLevel: Int,
    val energyLevel: Int,
    val sessionDate: String,
    val trigger: String
)
