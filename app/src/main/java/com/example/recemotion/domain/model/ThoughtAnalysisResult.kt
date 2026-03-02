package com.example.recemotion.domain.model

/**
 * Final structured analysis from the LLM.
 */
data class ThoughtAnalysisResult(
    val premises: List<String> = emptyList(),
    val emotions: List<String> = emptyList(),
    val inferences: List<String> = emptyList(), // Legacy
    val statedFacts: List<String> = emptyList(),
    val assumptions: List<Assumption> = emptyList(),
    val possibleBiases: List<BiasDetection> = emptyList(),
    val missingPerspectives: List<MissingPerspective> = emptyList()
)

data class Assumption(
    val text: String,
    val importance: Int,
    val verificationGoal: String
)
