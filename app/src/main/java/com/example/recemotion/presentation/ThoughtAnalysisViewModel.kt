package com.example.recemotion.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recemotion.data.llm.ThoughtAnalysisJsonParser
import com.example.recemotion.domain.model.AnalysisUpdate
import com.example.recemotion.domain.model.DiagnosticMessage
import com.example.recemotion.domain.model.InferenceProgress
import com.example.recemotion.domain.repository.ThoughtRepository
import com.example.recemotion.domain.service.LLMInferenceService
import com.example.recemotion.domain.usecase.AnalyzeThoughtUseCase
import com.example.recemotion.domain.usecase.ConversationUpdateEvent
import com.example.recemotion.domain.usecase.ManageConversationUseCase
import com.example.recemotion.domain.usecase.SystemDiagnosticUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Thought Structuring Engine.
 * Injected by Hilt; depends only on domain interfaces and stable use cases.
 */
@HiltViewModel
class ThoughtAnalysisViewModel @Inject constructor(
    private val analyzeThoughtUseCase: AnalyzeThoughtUseCase,
    private val manageConversationUseCase: ManageConversationUseCase,
    private val diagnosticUseCase: SystemDiagnosticUseCase,
    private val repository: ThoughtRepository,
    private val llmService: LLMInferenceService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ThoughtAnalysisUiState())
    val uiState: StateFlow<ThoughtAnalysisUiState> = _uiState.asStateFlow()

    private val _historyItems = MutableStateFlow<List<ConversationDisplayItem>>(emptyList())
    val historyItems: StateFlow<List<ConversationDisplayItem>> = _historyItems.asStateFlow()

    /** Partial LLM token stream — forwarded from [LLMInferenceService]. */
    val partialResults: SharedFlow<String> = llmService.partialResults

    /** LLM inference progress — forwarded from [LLMInferenceService]. */
    val progress: StateFlow<InferenceProgress> = llmService.progress

    private val jsonParser = ThoughtAnalysisJsonParser()
    private var analyzeJob: Job? = null

    init {
        loadHistory()
        runDiagnostic()
    }

    /** Initializes the LLM model (delegates to [LLMInferenceService]). */
    fun initModel() = llmService.initModel()

    private fun loadHistory() {
        viewModelScope.launch {
            combine(
                repository.getAllTopics(),
                repository.getAllEntries(),
                repository.getAllToDos(),
                _uiState
            ) { topics, entries, allTodos, state ->
                val items = mutableListOf<ConversationDisplayItem>()

                items.addAll(state.systemLogs)

                for (topic in topics) {
                    items.add(ConversationDisplayItem.TopicHeader(topic.id, topic.title, topic.isResolved))

                    val topicTodos = allTodos.filter { it.topicId == topic.id }
                    for (todo in topicTodos) {
                        items.add(
                            ConversationDisplayItem.ToDoItem(
                                todo.id, todo.topicId, todo.description, todo.isCompleted, todo.resultNotes
                            )
                        )
                    }

                    val topicEntries = entries.filter { it.topicId == topic.id }
                    for (entry in topicEntries) {
                        val analysis = repository.getAnalysisForEntry(entry.id)
                        val result = analysis?.let {
                            runCatching { jsonParser.parse(it.analysisJson) }.getOrNull()
                        }
                        items.add(ConversationDisplayItem.ThoughtAnalysis(entry.id, entry.rawText, result))
                    }

                    if (topic.isResolved && !topic.resolutionResult.isNullOrBlank()) {
                        items.add(
                            ConversationDisplayItem.SystemMessage(
                                id = topic.id * -1,
                                message = "RESOLVED: ${topic.resolutionResult}",
                                isError = false
                            )
                        )
                    }
                }
                items
            }.collect {
                _historyItems.value = it
            }
        }
    }

    private fun runDiagnostic() {
        val diagnosticLogs = diagnosticUseCase.runDiagnostic().map { msg ->
            ConversationDisplayItem.SystemMessage(
                id = System.nanoTime(),
                message = msg.text,
                isError = msg.isError
            )
        }
        _uiState.value = _uiState.value.copy(
            systemLogs = _uiState.value.systemLogs + diagnosticLogs
        )
    }

    fun analyze(text: String) {
        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true, error = null)

            manageConversationUseCase.processInput(text).collect { event ->
                when (event) {
                    is ConversationUpdateEvent.Analyzing -> {
                        _uiState.value = _uiState.value.copy(analyzingMessage = event.message)
                    }
                    is ConversationUpdateEvent.Done -> {
                        val topic = repository.getTopicById(event.topicId)
                        _uiState.value = _uiState.value.copy(
                            currentTopicId = event.topicId,
                            isNewTopicDetected = event.isNewTopic,
                            topicTitle = topic?.title
                        )
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
        analyzeThoughtUseCase.execute(text, entryId).collect { update ->
            val preserved = _uiState.value
            _uiState.value = when (update) {
                is AnalysisUpdate.Analyzing -> preserved.copy(isAnalyzing = true)
                is AnalysisUpdate.Progress -> preserved.copy(
                    isAnalyzing = true,
                    thoughtTree = update.structure,
                    partialStreamingText = update.partial
                )
                is AnalysisUpdate.Complete -> preserved.copy(
                    isAnalyzing = false,
                    thoughtTree = update.structure,
                    partialStreamingText = update.fullText,
                    finalResult = update.result
                )
                is AnalysisUpdate.Error -> preserved.copy(
                    isAnalyzing = false,
                    error = update.message
                )
            }
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

        val topicId = _uiState.value.currentTopicId ?: return

        viewModelScope.launch {
            pushSystemMessage("Generating To-Do list to verify assumptions...")

            val assumptionsText = result.assumptions.joinToString("\n") {
                "- ${it.text} (Goal: ${it.verificationGoal})"
            }

            val prompt = """
You are a coach. Based on the following potential assumptions and their verification goals, generate concrete, actionable To-Do tasks (maximum 3 items) for the user to verify if these assumptions are true or false.
Each task should be a single line starting with "- ".

Assumptions:
$assumptionsText

Output:
- [Task description]
""".trimIndent()

            analyzeThoughtUseCase.execute(prompt).collect { update ->
                if (update is AnalysisUpdate.Complete && update.fullText.isNotBlank()) {
                    val lines = update.fullText.lines()
                        .filter { it.trim().startsWith("-") }
                        .map { it.trim().removePrefix("-").trim() }

                    val timestamp = System.currentTimeMillis()
                    for (line in lines) {
                        repository.insertToDo(topicId, line, timestamp)
                    }
                    pushSystemMessage("Generated ${lines.size} tasks for this topic.")
                }
            }
        }
    }

    fun toggleToDo(todoId: Long, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.updateToDoStatus(todoId, isCompleted)
        }
    }

    fun resolveTopic(topicId: Long, resolution: String) {
        viewModelScope.launch {
            repository.resolveTopic(topicId, resolution, System.currentTimeMillis())
            pushSystemMessage("Topic resolved: $resolution")
        }
    }
}
