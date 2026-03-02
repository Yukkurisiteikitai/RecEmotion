package com.example.recemotion.domain.model

data class ThoughtEntry(
    val id: Long,
    val topicId: Long?,
    val rawText: String,
    val treeJson: String,
    val createdAt: Long
)
