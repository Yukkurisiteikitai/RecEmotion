package com.example.recemotion.domain.model

data class ConversationTopic(
    val id: Long,
    val title: String,
    val isResolved: Boolean,
    val resolutionResult: String?,
    val createdAt: Long,
    val updatedAt: Long
)
