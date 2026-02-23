package com.example.recemotion.data.parser

import android.util.Log
import com.example.recemotion.domain.model.LogicalFlowAnalysis

/**
 * Prototype for detecting topic changes using Cabocha structural data and LLM semantic data.
 */
class TopicChangeDetector {

    companion object {
        private const val TAG = "TopicChangeDetector"
        private const val STRUCTURAL_THRESHOLD = 0.5 // Structural similarity threshold
    }

    /**
     * Evaluates if the current input is a continuation of the previous topic.
     * returns 0.0 to 1.0 (1.0 = highly likely a new topic)
     */
    fun evaluateStructuralChange(current: LogicalFlowAnalysis, previous: LogicalFlowAnalysis?): Double {
        if (previous == null) return 0.0

        val currentSubjects = current.sentences.map { it.structure.subject }.filter { it.isNotBlank() }.toSet()
        val prevSubjects = previous.sentences.map { it.structure.subject }.filter { it.isNotBlank() }.toSet()

        if (currentSubjects.isEmpty() || prevSubjects.isEmpty()) return 0.3 // Uncertain

        val intersection = currentSubjects.intersect(prevSubjects)
        val union = currentSubjects.union(prevSubjects)

        val similarity = intersection.size.toDouble() / union.size.toDouble()
        Log.d(TAG, "Structural Similarity (Jaccard on Subjects): $similarity")

        return 1.0 - similarity
    }

    /**
     * Builds a prompt to ask LLM if the topic has changed.
     */
    fun buildTopicChangePrompt(currentText: String, previousText: String): String {
        return """
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
}
