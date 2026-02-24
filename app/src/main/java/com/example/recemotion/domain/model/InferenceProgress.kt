package com.example.recemotion.domain.model

enum class LlmStage { IDLE, LOADING, GENERATING, DONE, ERROR }

data class InferenceProgress(
    val stage: LlmStage,
    val current: Long,
    val total: Long,
    val message: String
)
