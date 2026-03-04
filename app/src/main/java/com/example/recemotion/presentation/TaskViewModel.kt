package com.example.recemotion.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recemotion.data.db.HypothesisEntity
import com.example.recemotion.data.db.TaskEntity
import com.example.recemotion.data.db.TaskPhaseEntity
import com.example.recemotion.data.db.TimeAggregationEntity
import com.example.recemotion.data.repository.HypothesisRepository
import com.example.recemotion.data.repository.TaskPhaseRepository
import com.example.recemotion.data.repository.TaskRepository
import com.example.recemotion.data.repository.TimeAggregationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskRepo: TaskRepository,
    private val phaseRepo: TaskPhaseRepository,
    private val hypothesisRepo: HypothesisRepository,
    private val timeRepo: TimeAggregationRepository
) : ViewModel() {

    val allTasks: StateFlow<List<TaskEntity>> = taskRepo.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTasks: StateFlow<List<TaskEntity>> = taskRepo.getTasksByStatus("IN_PROGRESS")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val doneTasks: StateFlow<List<TaskEntity>> = taskRepo.getTasksByStatus("DONE")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _wizardTaskId = MutableStateFlow<Long?>(null)
    val wizardTaskId: StateFlow<Long?> = _wizardTaskId.asStateFlow()

    // Step 0: Observation — creates the task record
    fun createTask(title: String, description: String, importance: Int, urgency: Int, scope: Int) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val taskId = taskRepo.createTask(
                TaskEntity(
                    title = title,
                    description = description,
                    importance = importance,
                    urgency = urgency,
                    scope = scope,
                    createdAt = now,
                    targetCompletionDate = null,
                    currentPhase = "OBSERVATION",
                    status = "IN_PROGRESS",
                    actualMinutes = 0
                )
            )
            phaseRepo.createPhase(
                TaskPhaseEntity(
                    taskId = taskId,
                    phaseType = "OBSERVATION",
                    status = "DONE",
                    startTime = now,
                    endTime = now,
                    notes = description,
                    order = 0
                )
            )
            _wizardTaskId.value = taskId
        }
    }

    // Step 1: Strategy — records hypothesis, expected outcome, and planned time
    fun saveStrategy(hypothesis: String, expectedOutcome: String, plannedMinutes: Int) {
        val taskId = _wizardTaskId.value ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val phaseId = phaseRepo.createPhase(
                TaskPhaseEntity(
                    taskId = taskId,
                    phaseType = "STRATEGY",
                    status = "DONE",
                    startTime = now,
                    endTime = now,
                    notes = hypothesis,
                    order = 1
                )
            )
            hypothesisRepo.createHypothesis(
                HypothesisEntity(
                    phaseId = phaseId,
                    hypothesis = hypothesis,
                    expectedOutcome = expectedOutcome,
                    actualOutcome = null,
                    gapAnalysis = null
                )
            )
            val plannedSeconds = plannedMinutes * 60L
            timeRepo.createAggregation(
                TimeAggregationEntity(
                    taskId = taskId,
                    totalSeconds = 0,
                    plannedSeconds = plannedSeconds,
                    variance = -plannedSeconds,
                    efficiency = 0.0
                )
            )
            val task = taskRepo.getTaskById(taskId) ?: return@launch
            taskRepo.updateTask(task.copy(currentPhase = "STRATEGY"))
        }
    }

    // Step 2: Practice — records actual time and notes
    fun completePractice(notes: String, actualMinutes: Int) {
        val taskId = _wizardTaskId.value ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            phaseRepo.createPhase(
                TaskPhaseEntity(
                    taskId = taskId,
                    phaseType = "PRACTICE",
                    status = "DONE",
                    startTime = now,
                    endTime = now,
                    notes = notes,
                    order = 2
                )
            )
            val actualSeconds = actualMinutes * 60L
            val existing = timeRepo.getByTask(taskId).firstOrNull()
            if (existing != null) {
                val variance = actualSeconds - existing.plannedSeconds
                val efficiency = if (existing.plannedSeconds > 0 && actualSeconds > 0) {
                    existing.plannedSeconds.toDouble() / actualSeconds
                } else 1.0
                timeRepo.updateAggregation(
                    existing.copy(totalSeconds = actualSeconds, variance = variance, efficiency = efficiency)
                )
            } else {
                timeRepo.createAggregation(
                    TimeAggregationEntity(
                        taskId = taskId,
                        totalSeconds = actualSeconds,
                        plannedSeconds = 0,
                        variance = actualSeconds,
                        efficiency = 1.0
                    )
                )
            }
            val task = taskRepo.getTaskById(taskId) ?: return@launch
            taskRepo.updateTask(task.copy(currentPhase = "PRACTICE", actualMinutes = actualMinutes))
        }
    }

    // Step 3: Retro — records actual outcome and gap analysis, marks task DONE
    fun completeTask(actualOutcome: String, gapAnalysis: String) {
        val taskId = _wizardTaskId.value ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            phaseRepo.createPhase(
                TaskPhaseEntity(
                    taskId = taskId,
                    phaseType = "RETRO",
                    status = "DONE",
                    startTime = now,
                    endTime = now,
                    notes = gapAnalysis,
                    order = 3
                )
            )
            val hypotheses = hypothesisRepo.getByTask(taskId).first()
            hypotheses.firstOrNull()?.let { hypo ->
                hypothesisRepo.updateHypothesis(
                    hypo.copy(actualOutcome = actualOutcome, gapAnalysis = gapAnalysis)
                )
            }
            val task = taskRepo.getTaskById(taskId) ?: return@launch
            taskRepo.updateTask(task.copy(status = "DONE", currentPhase = "RETRO"))
            _wizardTaskId.value = null
        }
    }

    fun resetWizard() {
        _wizardTaskId.value = null
    }
}
