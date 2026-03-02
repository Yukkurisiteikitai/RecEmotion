package com.example.recemotion.domain.usecase

import android.util.Log
import com.example.recemotion.domain.model.ConversationUpdateEvent
import com.example.recemotion.domain.model.LlmStreamEvent
import com.example.recemotion.domain.repository.ThoughtRepository
import com.example.recemotion.domain.service.LLMInferenceService
import com.example.recemotion.domain.service.LogicalFlowService
import com.example.recemotion.domain.service.TopicChangeService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ManageConversationUseCase @Inject constructor(
    private val repository: ThoughtRepository,
    private val flowService: LogicalFlowService,
    private val topicChangeService: TopicChangeService,
    private val llmService: LLMInferenceService
) {
    companion object {
        private const val TAG = "ManageConversationUseCase"
    }

    fun processInput(text: String): Flow<ConversationUpdateEvent> = channelFlow {
        if (text.isBlank()) return@channelFlow

        send(ConversationUpdateEvent.Analyzing("Starting Analysis..."))

        // 1. Structural analysis
        val currentFlow = flowService.analyze(text)
        val activeTopic = repository.getActiveTopic()

        var isNewTopic = activeTopic == null
        var suggestedTitle = "New Topic"

        if (activeTopic != null) {
            val lastEntry = repository.getLatestEntryForTopic(activeTopic.id)
            val lastFlow = lastEntry?.let { flowService.analyze(it.rawText) }

            val structuralScore = topicChangeService.evaluateStructuralChange(currentFlow, lastFlow)
            Log.d(TAG, "Structural Change Score: $structuralScore")

            // 2. Semantic analysis (LLM) when structural change is ambiguous
            if (structuralScore > 0.4) {
                send(ConversationUpdateEvent.Analyzing("Evaluating topic shift..."))
                val prompt = topicChangeService.buildTopicChangePrompt(text, lastEntry?.rawText ?: "")
                val llmResult = llmService.analyzeThoughtStructure(prompt)
                    .first { it is LlmStreamEvent.Done } as LlmStreamEvent.Done

                val topicChangeResult = topicChangeService.parseTopicChangeResponse(llmResult.fullText)
                isNewTopic = topicChangeResult.isNewTopic
                suggestedTitle = topicChangeResult.suggestedTitle
                Log.d(TAG, "LLM Topic Decision: $isNewTopic, Title: $suggestedTitle")
            }
        }

        // 3. Persist to database via repository
        val timestamp = System.currentTimeMillis()
        val finalTopicId = if (isNewTopic) {
            repository.insertTopic(suggestedTitle, timestamp)
        } else {
            activeTopic!!.id
        }

        val entryId = repository.storeEntry(
            topicId = finalTopicId,
            rawText = text,
            treeJson = "{}",
            timestamp = timestamp
        )

        repository.updateTopicTimestamp(finalTopicId, timestamp)

        send(ConversationUpdateEvent.Done(finalTopicId, isNewTopic, entryId))
    }
}
