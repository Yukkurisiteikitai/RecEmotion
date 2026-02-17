package com.example.recemotion.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recemotion.domain.usecase.AnalyzeThoughtUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Thought Structuring Engine.
 */
class ThoughtAnalysisViewModel(
    private val analyzeThoughtUseCase: AnalyzeThoughtUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ThoughtAnalysisUiState())
    val uiState: StateFlow<ThoughtAnalysisUiState> = _uiState.asStateFlow()

    private var analyzeJob: Job? = null

    fun analyze(text: String) {
        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch {
            analyzeThoughtUseCase.execute(text).collect { state ->
                _uiState.value = state
            }
        }
    }
}
