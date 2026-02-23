package com.example.recemotion.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recemotion.data.db.AppDatabase
import com.example.recemotion.data.llm.ThoughtAnalysisJsonParser
import com.example.recemotion.domain.usecase.AnalyzeThoughtUseCase
import com.example.recemotion.domain.usecase.ConversationUpdateEvent
import com.example.recemotion.domain.usecase.ManageConversationUseCase
import com.example.recemotion.domain.usecase.SystemDiagnosticUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * ViewModel for the Thought Structuring Engine.
 */
class ThoughtAnalysisViewModel(
    private val analyzeThoughtUseCase: AnalyzeThoughtUseCase,
    private val manageConversationUseCase: ManageConversationUseCase,
    private val diagnosticUseCase: SystemDiagnosticUseCase,
    private val db: AppDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ThoughtAnalysisUiState())
    val uiState: StateFlow<ThoughtAnalysisUiState> = _uiState.asStateFlow()

    private val _historyItems = MutableStateFlow<List<ConversationDisplayItem>>(emptyList())
    val historyItems: StateFlow<List<ConversationDisplayItem>> = _historyItems.asStateFlow()

    private val jsonParser = ThoughtAnalysisJsonParser()
    private var analyzeJob: Job? = null

    init {
        loadHistory()
        runDiagnostic()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            combine(
                db.conversationTopicDao().getAllTopics(),
                db.thoughtEntryDao().getAllEntries(),
                _uiState
            ) { topics, entries, state ->
                val items = mutableListOf<ConversationDisplayItem>()
                
                // Add system logs at the top
                items.addAll(state.systemLogs)

                for (topic in topics) {
                    items.add(ConversationDisplayItem.TopicHeader(topic.id, topic.title))
                    val topicEntries = entries.filter { it.topicId == topic.id }
                    for (entry in topicEntries) {
                        val analysis = db.thoughtAnalysisDao().getAnalysisForEntry(entry.id)
                        val result = analysis?.let { 
                            runCatching { jsonParser.parse(it.analysisJson) }.getOrNull()
                        }
                        items.add(ConversationDisplayItem.ThoughtAnalysis(entry.id, entry.rawText, result))
                    }
                }
                items
            }.collect {
                _historyItems.value = it
            }
        }
    }

    private fun runDiagnostic() {
        val diagnosticLogs = diagnosticUseCase.runDiagnostic()
        _uiState.value = _uiState.value.copy(
            systemLogs = _uiState.value.systemLogs + diagnosticLogs
        )
    }

    fun analyze(text: String) {
        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch {
            _uiState.value = ThoughtAnalysisUiState(isAnalyzing = true)

            manageConversationUseCase.processInput(text).collect { event ->
                when (event) {
                    is ConversationUpdateEvent.Analyzing -> {
                        _uiState.value = _uiState.value.copy(analyzingMessage = event.message)
                    }
                    is ConversationUpdateEvent.Done -> {
                        val topic = db.conversationTopicDao().getTopicById(event.topicId)
                        _uiState.value = _uiState.value.copy(
                            currentTopicId = event.topicId,
                            isNewTopicDetected = event.isNewTopic,
                            topicTitle = topic?.title
                        )
                        
                        // Proceed to detailed structural/LLM analysis
                        startDetailedAnalysis(text, event.entryId)
                    }
                    is ConversationUpdateEvent.Error -> {
                        _uiState.value = _uiState.value.copy(isAnalyzing = false, error = event.message)
                    }
                }
            }
        }
    }

    private suspend fun startDetailedAnalysis(text: String, entryId: Long) {
        analyzeThoughtUseCase.execute(text, entryId).collect { state ->
            _uiState.value = state.copy(
                currentTopicId = _uiState.value.currentTopicId,
                isNewTopicDetected = _uiState.value.isNewTopicDetected,
                topicTitle = _uiState.value.topicTitle,
                systemLogs = _uiState.value.systemLogs
            )
        }
    }

    fun dismissTopicNotification() {
        _uiState.value = _uiState.value.copy(isNewTopicDetected = false)
    }

    fun pushSystemMessage(message: String, isError: Boolean = false) {
        val newLog = ConversationDisplayItem.SystemMessage(
            id = System.nanoTime(),
            message = message,
            isError = isError
        )
        _uiState.value = _uiState.value.copy(
            systemLogs = _uiState.value.systemLogs + newLog
        )
    }

    fun generateToDo(item: ConversationDisplayItem.ThoughtAnalysis) {
        val result = item.result ?: return
        if (result.assumptions.isEmpty()) return

        viewModelScope.launch {
            pushSystemMessage("Generating To-Do list to verify assumptions...")
            
            val assumptionsText = result.assumptions.joinToString("\n") { 
                "- ${it.text} (Goal: ${it.verificationGoal})" 
            }
            
            val prompt = """
You are a coach. Based on the following potential assumptions and their verification goals, generate a concrete, actionable To-Do list (maximum 3 items) for the user to verify if these assumptions are true or false.

Assumptions:
$assumptionsText

Output format:
- [ ] Task 1
- [ ] Task 2
- [ ] Task 3
""".trimIndent()

            analyzeThoughtUseCase.execute(prompt).collect { state ->
                if (state.finalResult != null || state.partialStreamingText.isNotBlank()) {
                    // We reuse system message or a specific To-Do item type
                    // For now, let's push as a system message when done
                }
                if (!state.isAnalyzing && state.partialStreamingText.isNotBlank()) {
                    pushSystemMessage("Verification Plan:\n${state.partialStreamingText}")
                }
            }
        }
    }
}
