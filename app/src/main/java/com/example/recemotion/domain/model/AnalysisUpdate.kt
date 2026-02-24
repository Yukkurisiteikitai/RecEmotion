package com.example.recemotion.domain.model

/**
 * Domain-level events emitted by AnalyzeThoughtUseCase.
 * Presentation layer maps these to ThoughtAnalysisUiState.
 */
sealed class AnalysisUpdate {
    object Analyzing : AnalysisUpdate()
    data class Progress(val structure: ThoughtStructure, val partial: String) : AnalysisUpdate()
    data class Complete(
        val structure: ThoughtStructure,
        val fullText: String,
        val result: ThoughtAnalysisResult?
    ) : AnalysisUpdate()
    data class Error(val message: String) : AnalysisUpdate()
}
