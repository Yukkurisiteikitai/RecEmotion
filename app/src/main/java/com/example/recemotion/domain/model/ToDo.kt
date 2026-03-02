package com.example.recemotion.domain.model

data class ToDo(
    val id: Long,
    val topicId: Long,
    val description: String,
    val isCompleted: Boolean,
    val resultNotes: String?,
    val createdAt: Long
)
