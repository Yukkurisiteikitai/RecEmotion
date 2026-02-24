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
                db.todoDao().getAllToDos(),
                _uiState
            ) { topics, entries, allTodos, state ->
                val items = mutableListOf<ConversationDisplayItem>()
                
                // Add system logs at the top
                items.addAll(state.systemLogs)

                for (topic in topics) {
                    items.add(ConversationDisplayItem.TopicHeader(topic.id, topic.title, topic.isResolved))
                    
                    // Filter ToDos for this topic
                    val topicTodos = allTodos.filter { it.topicId == topic.id }
                    for (todo in topicTodos) {
                        items.add(ConversationDisplayItem.ToDoItem(
                            todo.id, todo.topicId, todo.description, todo.isCompleted, todo.resultNotes
                        ))
                    }

                    val topicEntries = entries.filter { it.topicId == topic.id }
                    for (entry in topicEntries) {
                        val analysis = db.thoughtAnalysisDao().getAnalysisForEntry(entry.id)
                        val result = analysis?.let { 
                            runCatching { jsonParser.parse(it.analysisJson) }.getOrNull()
                        }
                        items.add(ConversationDisplayItem.ThoughtAnalysis(entry.id, entry.rawText, result))
                    }

                    if (topic.isResolved && !topic.resolutionResult.isNullOrBlank()) {
                        items.add(ConversationDisplayItem.SystemMessage(
                            id = topic.id * -1, // Unique enough for display
                            message = "RESOLVED: ${topic.resolutionResult}",
                            isError = false
                        ))
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
            _uiState.value = _uiState.value.copy(isAnalyzing = true, error = null)

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

            analyzeThoughtUseCase.execute(prompt).collect { state ->
                if (!state.isAnalyzing && state.partialStreamingText.isNotBlank()) {
                    val lines = state.partialStreamingText.lines()
                        .filter { it.trim().startsWith("-") }
                        .map { it.trim().removePrefix("-").trim() }
                    
                    for (line in lines) {
                        db.todoDao().insertToDo(com.example.recemotion.data.db.ToDoEntity(
                            topicId = topicId,
                            description = line,
                            createdAt = System.currentTimeMillis()
                        ))
                    }
                    pushSystemMessage("Generated ${lines.size} tasks for this topic.")
                }
            }
        }
    }

    fun toggleToDo(todoId: Long, isCompleted: Boolean) {
        viewModelScope.launch {
            val todos = _historyItems.value.filterIsInstance<ConversationDisplayItem.ToDoItem>()
            val item = todos.find { it.id == todoId } ?: return@launch
            
            db.todoDao().updateToDoStatus(todoId, isCompleted)
        }
    }

    fun resolveTopic(topicId: Long, resolution: String) {
        viewModelScope.launch {
            db.conversationTopicDao().resolveTopic(topicId, resolution, System.currentTimeMillis())
            pushSystemMessage("Topic resolved: $resolution")
        }
    }
}
