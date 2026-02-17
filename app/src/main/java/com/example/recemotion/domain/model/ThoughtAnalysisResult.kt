package com.example.recemotion.domain.model

/**
 * Final structured analysis from the LLM.
 */
data class ThoughtAnalysisResult(
    val premises: List<String> = emptyList(),
    val emotions: List<String> = emptyList(),
    val inferences: List<String> = emptyList(),
    val possibleBiases: List<BiasDetection> = emptyList(),
    val missingPerspectives: List<MissingPerspective> = emptyList()
)
