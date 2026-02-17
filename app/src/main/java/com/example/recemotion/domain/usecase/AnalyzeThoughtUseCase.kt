package com.example.recemotion.domain.usecase

import com.example.recemotion.data.llm.LlmStreamEvent
import com.example.recemotion.data.llm.ThoughtAnalysisJsonParser
import com.example.recemotion.data.llm.ThoughtPromptBuilder
import com.example.recemotion.data.parser.CabochaThoughtMapper
import com.example.recemotion.data.parser.DependencyParser
import com.example.recemotion.data.repository.ThoughtRepository
import com.example.recemotion.data.serialization.ThoughtStructureJsonAdapter
import com.example.recemotion.domain.model.ThoughtStructure
import com.example.recemotion.presentation.ThoughtAnalysisUiState
import com.example.recemotion.LLMInferenceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Orchestrates the thought structuring analysis flow.
 */
class AnalyzeThoughtUseCase(
    private val parser: DependencyParser,
    private val mapper: CabochaThoughtMapper,
    private val promptBuilder: ThoughtPromptBuilder,
    private val llmHelper: LLMInferenceHelper,
    private val jsonParser: ThoughtAnalysisJsonParser,
    private val repository: ThoughtRepository,
    private val serializer: ThoughtStructureJsonAdapter
) {

    fun execute(text: String): Flow<ThoughtAnalysisUiState> = channelFlow {
        if (text.isBlank()) {
            send(ThoughtAnalysisUiState(error = "Input is empty"))
            return@channelFlow
        }

        send(ThoughtAnalysisUiState(isAnalyzing = true))

        val structure: ThoughtStructure = withContext(Dispatchers.Default) {
            val parsed = parser.parse(text)
            mapper.map(parsed)
        }

        send(ThoughtAnalysisUiState(isAnalyzing = true, thoughtTree = structure))

        val prompt = promptBuilder.build(structure)
        val stream = llmHelper.analyzeThoughtStructure(prompt)
        val partialBuilder = StringBuilder()

        stream.collect { event ->
            when (event) {
                is LlmStreamEvent.Delta -> {
                    partialBuilder.append(event.text)
                    send(
                        ThoughtAnalysisUiState(
                            isAnalyzing = true,
                            thoughtTree = structure,
                            partialStreamingText = partialBuilder.toString()
                        )
                    )
                }
                is LlmStreamEvent.Done -> {
                    val result = parseResultSafe(event.fullText)
                    val timestamp = System.currentTimeMillis()
                    withContext(Dispatchers.IO) {
                        val treeJson = serializer.toJson(structure)
                        val entryId = repository.storeEntry(text, treeJson, timestamp)
                        val resultJson = runCatching {
                            JSONObject(event.fullText).toString()
                        }.getOrElse { event.fullText }
                        repository.storeAnalysis(entryId, resultJson, timestamp)
                    }
                    send(
                        ThoughtAnalysisUiState(
                            isAnalyzing = false,
                            thoughtTree = structure,
                            partialStreamingText = event.fullText,
                            finalResult = result
                        )
                    )
                }
                is LlmStreamEvent.Error -> {
                    send(
                        ThoughtAnalysisUiState(
                            isAnalyzing = false,
                            thoughtTree = structure,
                            error = event.message
                        )
                    )
                }
            }
        }
    }

    private fun parseResultSafe(jsonText: String) = runCatching {
        jsonParser.parse(jsonText)
    }.getOrNull()
}
