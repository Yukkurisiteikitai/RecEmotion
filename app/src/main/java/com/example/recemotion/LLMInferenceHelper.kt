package com.example.recemotion

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class LLMInferenceHelper(val context: Context) {
    private var isInitialized = false
    private var llmInference: LlmInference? = null
    private var lastResultText = ""

    private val modelFilename = ModelDownloadHelper.MODEL_FILENAME
    private val _partialResults = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val partialResults: SharedFlow<String> = _partialResults.asSharedFlow()

    private val _progress = MutableStateFlow(
        InferenceProgress(
            stage = Stage.IDLE,
            current = 0,
            total = 0,
            message = "Idle"
        )
    )
    val progress: StateFlow<InferenceProgress> = _progress.asStateFlow()

    enum class Stage {
        IDLE,
        LOADING,
        GENERATING,
        DONE,
        ERROR
    }

    fun initModel() {
        updateProgress(stage = Stage.LOADING, current = 0, total = 0, message = "Loading model")

        val modelFile = resolveModelFile()
        if (modelFile == null) {
            val message = "Error: Model file not found. Place model.bin or model.task in Downloads or app internal storage."
            Log.e(TAG, message)
            _partialResults.tryEmit(message)
            updateProgress(stage = Stage.ERROR, message = "Error: model file not found")
            isInitialized = false
            return
        }

        close()

        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(MAX_TOTAL_TOKENS)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isInitialized = true
            updateProgress(stage = Stage.IDLE, message = "Model ready")
            Log.i(TAG, "MediaPipe LLM model initialized successfully")
            _partialResults.tryEmit("MediaPipe LLM model loaded successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPipe LLM model", e)
            _partialResults.tryEmit("Error: failed to initialize MediaPipe LLM model.")
            updateProgress(stage = Stage.ERROR, message = "Error: failed to initialize model")
            isInitialized = false
        }
    }

    fun isModelInitialized(): Boolean {
        return isInitialized
    }

    fun generateResponse(prompt: String) {
        Log.d(TAG, "=== generateResponse called ===")
        Log.d(TAG, "isInitialized: $isInitialized")

        if (!isInitialized) {
            Log.w(TAG, "Model not initialized, attempting to initialize...")
            _partialResults.tryEmit("Model not initialized. Attempting to load...\n")
            initModel()
            if (!isInitialized) {
                Log.e(TAG, "Failed to initialize model")
                _partialResults.tryEmit("\nError: Failed to initialize MediaPipe LLM model. Please select a valid .bin or .task file.")
                return
            }
        }

        val inference = llmInference
        if (inference == null) {
            val errorMsg = "Error: MediaPipe LLM is not available. Please reinitialize the model."
            Log.e(TAG, errorMsg)
            _partialResults.tryEmit(errorMsg)
            updateProgress(stage = Stage.ERROR, message = "Error: model not available")
            return
        }

        lastResultText = ""
        updateProgress(stage = Stage.GENERATING, current = 0, total = 0, message = "Generating")
        _partialResults.tryEmit("\n--- MediaPipe LLM Response ---\n")

        try {
            val promptLimit = (MAX_TOTAL_TOKENS - OUTPUT_TOKENS_RESERVE).coerceAtLeast(1)
            val trimmedPrompt = trimPromptToTokenLimit(inference, prompt, promptLimit)
            val listener = ProgressListener<String> { result, done ->
                handleResult(result, done)
                if (!done) {
                    val tokenCount = estimateTokenCount(inference, result)
                    updateProgress(
                        stage = Stage.GENERATING,
                        current = tokenCount,
                        total = MAX_TOTAL_TOKENS.toLong(),
                        message = "Generating"
                    )
                }
            }
            inference.generateResponseAsync(trimmedPrompt, listener)
            Log.d(TAG, "MediaPipe LLM generation started")
        } catch (e: Exception) {
            Log.e(TAG, "Error generating response", e)
            _partialResults.tryEmit("Error: failed to generate response.")
            updateProgress(stage = Stage.ERROR, message = "Error: failed to generate")
        }
    }

    fun close() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing model", e)
        }
        isInitialized = false
        llmInference = null
        lastResultText = ""
        updateProgress(stage = Stage.IDLE, current = 0, total = 0, message = "Idle")
    }

    private fun resolveModelFile(): File? {
        val supportedExtensions = listOf("bin", "task")

        for (ext in supportedExtensions) {
            val internalFile = File(context.filesDir, "model.$ext")
            if (internalFile.exists()) {
                Log.i(TAG, "Found model in internal storage: ${internalFile.absolutePath}")
                return internalFile
            }
        }

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        for (ext in supportedExtensions) {
            val downloadsFile = File(downloadsDir, "model.$ext")
            if (downloadsFile.exists()) {
                Log.i(TAG, "Found model in Downloads: ${downloadsFile.absolutePath}")
                return downloadsFile
            }
        }

        val legacyFile = File(context.filesDir, modelFilename)
        if (legacyFile.exists() && legacyFile.extension.lowercase() in supportedExtensions) {
            Log.i(TAG, "Found legacy model: ${legacyFile.absolutePath}")
            return legacyFile
        }

        return null
    }

    private fun handleResult(result: Any?, done: Boolean) {
        val resultText = extractResultText(result)
        if (resultText.isNotEmpty()) {
            val delta = if (resultText.startsWith(lastResultText)) {
                resultText.substring(lastResultText.length)
            } else {
                resultText
            }

            if (delta.isNotEmpty()) {
                _partialResults.tryEmit(delta)
            }
            lastResultText = resultText
        }

        if (done) {
            updateProgress(stage = Stage.DONE, message = "Done")
        }
    }

    private fun extractResultText(result: Any?): String {
        return when (result) {
            null -> ""
            is String -> result
            else -> result.toString()
        }
    }

    private fun estimateTokenCount(inference: LlmInference, text: String): Long {
        return try {
            inference.sizeInTokens(text).toLong()
        } catch (e: Exception) {
            0L
        }
    }

    private fun trimPromptToTokenLimit(
        inference: LlmInference,
        prompt: String,
        maxPromptTokens: Int
    ): String {
        if (maxPromptTokens <= 0 || prompt.isEmpty()) return ""

        val fullTokens = inference.sizeInTokens(prompt)
        if (fullTokens <= maxPromptTokens) {
            return prompt
        }

        var low = 0
        var high = prompt.length
        var best = 0

        while (low <= high) {
            val mid = (low + high) ushr 1
            val candidate = prompt.substring(0, mid)
            val tokens = inference.sizeInTokens(candidate)
            if (tokens <= maxPromptTokens) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return if (best <= 0) "" else prompt.substring(0, best)
    }

    private fun updateProgress(
        stage: Stage? = null,
        current: Long? = null,
        total: Long? = null,
        message: String? = null
    ) {
        val previous = _progress.value
        val resolvedTotal = total?.let { if (it > 0L) it else 0L } ?: previous.total
        val resolvedMessage = message?.let { sanitizeAscii(it) } ?: previous.message

        _progress.value = InferenceProgress(
            stage = stage ?: previous.stage,
            current = current ?: previous.current,
            total = resolvedTotal,
            message = resolvedMessage
        )
    }

    companion object {
        const val TAG = "LLMInferenceHelper"
        private const val MAX_TOTAL_TOKENS = 512
        private const val OUTPUT_TOKENS_RESERVE = 96
    }

    private fun sanitizeAscii(message: String): String {
        return message.map { ch -> if (ch.code in 32..126) ch else '?' }.joinToString("")
    }
}

data class InferenceProgress(
    val stage: LLMInferenceHelper.Stage,
    val current: Long,
    val total: Long,
    val message: String
)