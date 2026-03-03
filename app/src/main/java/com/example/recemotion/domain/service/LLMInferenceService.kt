package com.example.recemotion.domain.service

import com.example.recemotion.domain.model.InferenceProgress
import com.example.recemotion.domain.model.LlmStreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain interface for LLM inference operations.
 * Abstracts the concrete LLM implementation from domain/use-case layers.
 *
 * Lifecycle management (initModel, close) is handled by the implementation
 * and Hilt's @Singleton scope — not exposed here.
 */
interface LLMInferenceService {

    /** Partial text tokens emitted during generation (for UI display). */
    val partialResults: Flow<String>

    /** Current inference progress state. */
    val progress: StateFlow<InferenceProgress>

    /** True when the LLM model has been successfully loaded and is ready for inference. */
    val isModelReady: StateFlow<Boolean>

    /**
     * Re-initializes the model from storage.
     * Call this when a new model file has been installed after the service started.
     */
    fun reloadModel()

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
}
