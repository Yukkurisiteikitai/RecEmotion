package com.example.recemotion.presentation

import com.example.recemotion.domain.model.ThoughtAnalysisResult
import com.example.recemotion.domain.model.ThoughtStructure

/**
 * UI state for thought analysis flow.
 */
data class ThoughtAnalysisUiState(
    val isAnalyzing: Boolean = false,
    val analyzingMessage: String? = null,
    val thoughtTree: ThoughtStructure? = null,
    val partialStreamingText: String = "",
    val finalResult: ThoughtAnalysisResult? = null,
    val currentTopicId: Long? = null,
    val topicTitle: String? = null,
    val isNewTopicDetected: Boolean = false,
    val systemLogs: List<ConversationDisplayItem.SystemMessage> = emptyList(),
    val error: String? = null
)
