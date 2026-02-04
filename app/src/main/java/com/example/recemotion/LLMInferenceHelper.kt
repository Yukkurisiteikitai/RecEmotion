package com.example.recemotion

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

class LLMInferenceHelper(val context: Context) {

    private var llmInference: LlmInference? = null
    
    // Model path in internal storage. User must push the .bin file here.
    // e.g., /data/data/com.example.recemotion/files/model.bin
    private val MODEL_PATH = "model.bin"

    private val _partialResults = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val partialResults: SharedFlow<String> = _partialResults.asSharedFlow()

    fun initModel() {
        val modelFile = File(context.filesDir, MODEL_PATH)
        if (!modelFile.exists()) {
            Log.e(TAG, "Model file not found at: ${modelFile.absolutePath}")
            _partialResults.tryEmit("Error: Model file not found. Please push 'model.bin' to internal storage.")
            return
        }

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(1024)
            .setResultListener { partialResult, done ->
                _partialResults.tryEmit(partialResult)
            }
            .build()

        try {
            llmInference = LlmInference.createFromOptions(context, options)
            Log.i(TAG, "LLM Inference initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LLM", e)
            _partialResults.tryEmit("Error initializing LLM: ${e.message}")
        }
    }

    fun generateResponse(prompt: String) {
        if (llmInference == null) {
            initModel()
            if (llmInference == null) return
        }

        try {
            // Async generation via callback (set in options)
            llmInference?.generateResponseAsync(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating response", e)
            _partialResults.tryEmit("Error generating response: ${e.message}")
        }
    }
    
    fun close() {
        llmInference = null
    }

    companion object {
        const val TAG = "LLMInferenceHelper"
    }
}
