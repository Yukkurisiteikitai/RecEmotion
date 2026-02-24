package com.example.recemotion.domain.usecase

import com.example.recemotion.data.llm.ThoughtAnalysisJsonParser
import com.example.recemotion.data.llm.ThoughtPromptBuilder
import com.example.recemotion.data.parser.CabochaThoughtMapper
import com.example.recemotion.data.parser.DependencyParser
import com.example.recemotion.data.serialization.ThoughtStructureJsonAdapter
import com.example.recemotion.domain.model.AnalysisUpdate
import com.example.recemotion.domain.model.LlmStreamEvent
import com.example.recemotion.domain.model.ThoughtStructure
import com.example.recemotion.domain.repository.ThoughtRepository
import com.example.recemotion.domain.service.LLMInferenceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

/**
 * Orchestrates the thought structuring analysis flow.
 * Depends only on domain interfaces and stable data-layer helpers.
 */
class AnalyzeThoughtUseCase @Inject constructor(
    private val parser: DependencyParser,
    private val mapper: CabochaThoughtMapper,
    private val promptBuilder: ThoughtPromptBuilder,
    private val llmService: LLMInferenceService,
    private val jsonParser: ThoughtAnalysisJsonParser,
    private val repository: ThoughtRepository,
    private val serializer: ThoughtStructureJsonAdapter
) {

    fun execute(text: String, entryId: Long? = null): Flow<AnalysisUpdate> = channelFlow {
        if (text.isBlank()) {
            send(AnalysisUpdate.Error("Input is empty"))
            return@channelFlow
        }

        send(AnalysisUpdate.Analyzing)

        val structure: ThoughtStructure = withContext(Dispatchers.Default) {
            val parsed = parser.parse(text)
            mapper.map(parsed)
        }

        send(AnalysisUpdate.Progress(structure, ""))

        val prompt = promptBuilder.build(structure)
        val stream = llmService.analyzeThoughtStructure(prompt)
        val partialBuilder = StringBuilder()

        stream.collect { event ->
            when (event) {
                is LlmStreamEvent.Delta -> {
                    partialBuilder.append(event.text)
                    send(AnalysisUpdate.Progress(structure, partialBuilder.toString()))
                }
                is LlmStreamEvent.Done -> {
                    val result = runCatching { jsonParser.parse(event.fullText) }.getOrNull()
                    val timestamp = System.currentTimeMillis()
                    withContext(Dispatchers.IO) {
                        val treeJson = serializer.toJson(structure)
                        val finalEntryId = if (entryId != null) {
                            val existing = repository.getEntryById(entryId)
                            if (existing != null) {
                                repository.updateEntry(entryId, treeJson)
                                entryId
                            } else {
                                repository.storeEntry(null, text, treeJson, timestamp)
                            }
                        } else {
                            repository.storeEntry(null, text, treeJson, timestamp)
                        }
                        val resultJson = runCatching {
                            JSONObject(event.fullText).toString()
                        }.getOrElse { event.fullText }
                        repository.storeAnalysis(finalEntryId, resultJson, timestamp)
                    }
                    send(AnalysisUpdate.Complete(structure, event.fullText, result))
                }
                is LlmStreamEvent.Error -> {
                    send(AnalysisUpdate.Error(event.message))
                }
            }
        }
    }
}
