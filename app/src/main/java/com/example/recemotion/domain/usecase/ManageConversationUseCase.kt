package com.example.recemotion.domain.usecase

import android.util.Log
import com.example.recemotion.LLMInferenceHelper
import com.example.recemotion.data.db.ConversationTopicDao
import com.example.recemotion.data.db.ConversationTopicEntity
import com.example.recemotion.data.db.ThoughtAnalysisDao
import com.example.recemotion.data.db.ThoughtAnalysisEntity
import com.example.recemotion.data.db.ThoughtEntryDao
import com.example.recemotion.data.db.ThoughtEntryEntity
import com.example.recemotion.data.llm.LlmStreamEvent
import com.example.recemotion.data.parser.LogicalFlowAnalyzer
import com.example.recemotion.data.parser.TopicChangeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ManageConversationUseCase(
    private val topicDao: ConversationTopicDao,
    private val entryDao: ThoughtEntryDao,
    private val analysisDao: ThoughtAnalysisDao,
    private val flowAnalyzer: LogicalFlowAnalyzer,
    private val topicChangeDetector: TopicChangeDetector,
    private val llmHelper: LLMInferenceHelper
) {
    companion object {
        private const val TAG = "ManageConversationUseCase"
    }

    fun processInput(text: String): Flow<ConversationUpdateEvent> = channelFlow {
        if (text.isBlank()) return@channelFlow

        send(ConversationUpdateEvent.Analyzing("Starting Analysis..."))

        // 1. Structural Analysis (Cabocha)
        val currentFlow = flowAnalyzer.analyze(text)
        val activeTopic = topicDao.getActiveTopic()
        
        var isNewTopic = activeTopic == null
        var suggestedTitle = "New Topic"

        if (activeTopic != null) {
            // Get last entry to compare
            // Note: In a real app, we might want to compare with a summary of the whole topic
            val lastEntry = entryDao.getEntriesByTopic(activeTopic.id).first().firstOrNull()
            val lastFlow = lastEntry?.let { flowAnalyzer.analyze(it.rawText) }
            
            val structuralScore = topicChangeDetector.evaluateStructuralChange(currentFlow, lastFlow)
            Log.d(TAG, "Structural Change Score: $structuralScore")

            // 2. Semantic Analysis (LLM) if structural change is ambiguous or high
            if (structuralScore > 0.4) {
                send(ConversationUpdateEvent.Analyzing("Evaluating topic shift..."))
                val prompt = topicChangeDetector.buildTopicChangePrompt(text, lastEntry?.rawText ?: "")
                val llmResult = llmHelper.analyzeThoughtStructure(prompt).first { it is LlmStreamEvent.Done } as LlmStreamEvent.Done
                
                try {
                    val json = JSONObject(llmResult.fullText)
                    isNewTopic = json.getBoolean("is_new_topic")
                    suggestedTitle = json.optString("suggested_title", "New Topic")
                    Log.d(TAG, "LLM Topic Decision: $isNewTopic, Title: $suggestedTitle")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse LLM topic decision", e)
                }
            }
        }

        // 3. Update Database
        val timestamp = System.currentTimeMillis()
        val finalTopicId = if (isNewTopic) {
            val newTopic = ConversationTopicEntity(
                title = suggestedTitle,
                createdAt = timestamp,
                updatedAt = timestamp
            )
            topicDao.insertTopic(newTopic)
        } else {
            activeTopic!!.id
        }

        // Store Entry
        val entryId = entryDao.insertEntry(
            ThoughtEntryEntity(
                topicId = finalTopicId,
                rawText = text,
                treeJson = "{}", // Placeholder for now
                createdAt = timestamp
            )
        )

        // Update Topic's updatedAt
        val topicToUpdate = topicDao.getTopicById(finalTopicId)
        if (topicToUpdate != null) {
            topicDao.updateTopic(topicToUpdate.copy(updatedAt = timestamp))
        }

        send(ConversationUpdateEvent.Done(finalTopicId, isNewTopic, entryId))
    }
}

sealed class ConversationUpdateEvent {
    data class Analyzing(val message: String) : ConversationUpdateEvent()
    data class Done(val topicId: Long, val isNewTopic: Boolean, val entryId: Long) : ConversationUpdateEvent()
    data class Error(val message: String) : ConversationUpdateEvent()
}
