package com.example.recemotion.data.llm

/**
 * Streaming events from LLM inference.
 */
sealed class LlmStreamEvent {
    data class Delta(val text: String) : LlmStreamEvent()
    data class Done(val fullText: String) : LlmStreamEvent()
    data class Error(val message: String) : LlmStreamEvent()
}
