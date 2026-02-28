package com.example.recemotion.domain.model

data class ThoughtAnalysis(
    val id: Long,
    val entryId: Long,
    val analysisJson: String,
    val createdAt: Long
)
