package com.example.recemotion.domain.service

import com.example.recemotion.domain.model.InferenceProgress
import com.example.recemotion.domain.model.LlmStreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain interface for LLM inference operations.
 * Abstracts the concrete LLM implementation from domain/use-case layers.
 */
interface LLMInferenceService {

    /** Partial text tokens emitted during generation (for UI display). */
    val partialResults: SharedFlow<String>

    /** Current inference progress state. */
    val progress: StateFlow<InferenceProgress>

    /** Initializes / reloads the model from storage. */
    fun initModel()

    /** Returns true if the model is loaded and ready. */
    fun isModelInitialized(): Boolean

    /**
     * Generates a free-form response and emits chunks to [partialResults].
     * Non-suspending; results stream via [partialResults].
     */
    fun generateResponse(prompt: String)

    /**
     * Analyzes thought structure and returns a [Flow] of [LlmStreamEvent].
     * Suitable for structured coroutine collection in use cases.
     */
    fun analyzeThoughtStructure(prompt: String): Flow<LlmStreamEvent>

    /** Releases model resources. */
    fun close()
}
