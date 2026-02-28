package com.example.recemotion.data.parser

import android.util.Log
import com.example.recemotion.domain.model.LogicalFlowAnalysis
import com.example.recemotion.domain.service.TopicChangeResult
import com.example.recemotion.domain.service.TopicChangeService
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [TopicChangeService].
 * Uses Jaccard similarity on sentence subjects for structural change detection,
 * plus an LLM prompt builder for semantic disambiguation.
 */
@Singleton
class TopicChangeDetectorImpl @Inject constructor() : TopicChangeService {

    companion object {
        private const val TAG = "TopicChangeDetectorImpl"
    }

    override fun evaluateStructuralChange(
        current: LogicalFlowAnalysis,
        previous: LogicalFlowAnalysis?
    ): Double {
        if (previous == null) return 0.0

        val currentSubjects = current.sentences.map { it.structure.subject }.filter { it.isNotBlank() }.toSet()
        val prevSubjects = previous.sentences.map { it.structure.subject }.filter { it.isNotBlank() }.toSet()

        if (currentSubjects.isEmpty() || prevSubjects.isEmpty()) return 0.3

        val intersection = currentSubjects.intersect(prevSubjects)
        val union = currentSubjects.union(prevSubjects)

        val similarity = intersection.size.toDouble() / union.size.toDouble()
        Log.d(TAG, "Structural Similarity (Jaccard on Subjects): $similarity")

        return 1.0 - similarity
    }

    override fun parseTopicChangeResponse(llmResponse: String): TopicChangeResult {
        return try {
            val json = JSONObject(llmResponse)
            TopicChangeResult(
                isNewTopic = json.getBoolean("is_new_topic"),
                suggestedTitle = json.optString("suggested_title", "New Topic")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse LLM topic decision", e)
            TopicChangeResult(isNewTopic = false)
        }
    }

    override fun buildTopicChangePrompt(currentText: String, previousText: String): String = """
You are a conversation analyzer. Compare the two texts below and determine if they are discussing the same topic or if a new topic has started.

Previous Text:
$previousText

Current Text:
$currentText

Respond with ONLY a JSON object:
{
  "is_new_topic": boolean,
  "confidence": 0.0 to 1.0,
  "reason": "short explanation",
  "suggested_title": "a short title for the current topic"
}
""".trimIndent()
}
