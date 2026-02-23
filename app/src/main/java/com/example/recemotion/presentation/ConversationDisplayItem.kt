package com.example.recemotion.presentation

import com.example.recemotion.domain.model.ThoughtAnalysisResult

sealed class ConversationDisplayItem {
    data class TopicHeader(val id: Long, val title: String) : ConversationDisplayItem()
    data class ThoughtAnalysis(
        val id: Long,
        val rawText: String,
        val result: ThoughtAnalysisResult?
    ) : ConversationDisplayItem()
    data class SystemMessage(
        val id: Long,
        val message: String,
        val isError: Boolean = false
    ) : ConversationDisplayItem()
}
