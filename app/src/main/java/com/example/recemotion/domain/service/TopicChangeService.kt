package com.example.recemotion.domain.service

import com.example.recemotion.domain.model.LogicalFlowAnalysis

/**
 * Domain interface for topic-change detection between conversation turns.
 */
interface TopicChangeService {

    /**
     * Computes structural similarity between two flow analyses.
     * Returns 0.0 (same topic) to 1.0 (completely different topic).
     */
    fun evaluateStructuralChange(
        current: LogicalFlowAnalysis,
        previous: LogicalFlowAnalysis?
    ): Double

    /**
     * Builds a prompt for the LLM to decide whether the topic has changed.
     */
    fun buildTopicChangePrompt(currentText: String, previousText: String): String

    /**
     * Parses the LLM's JSON response into a [TopicChangeResult].
     * Returns a default result (not a new topic) if parsing fails.
     */
    fun parseTopicChangeResponse(llmResponse: String): TopicChangeResult
}

data class TopicChangeResult(val isNewTopic: Boolean, val suggestedTitle: String = "New Topic")
