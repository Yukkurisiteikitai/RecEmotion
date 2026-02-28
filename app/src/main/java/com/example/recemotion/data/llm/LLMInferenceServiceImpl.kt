package com.example.recemotion.data.llm

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.recemotion.domain.model.InferenceProgress
import com.example.recemotion.domain.model.LlmStage
import com.example.recemotion.domain.model.LlmStreamEvent
import com.example.recemotion.domain.service.LLMInferenceService
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LLMInferenceServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LLMInferenceService {

    @Volatile private var isInitialized = false
    @Volatile private var llmInference: LlmInference? = null

    private val helperScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var initJob: Job? = null

    private val _partialResults = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val partialResults: Flow<String> = _partialResults.asSharedFlow()

    private val _progress = MutableStateFlow(
        InferenceProgress(stage = LlmStage.IDLE, current = 0, total = 0, message = "Idle")
    )
    override val progress: Flow<InferenceProgress> = _progress.asStateFlow()

    init {
        initModel()
    }

    private fun initModel() {
        Log.d(TAG, "1/5 [initModel] validate_file: searching for model file")
        updateProgress(stage = LlmStage.LOADING, current = 0, total = 0, message = "Loading model")

        val modelFile = resolveModelFile()
        if (modelFile == null) {
            Log.e(TAG, "1/5 [initModel] validate_file: NOT FOUND")
            val msg = "Error: Model file not found. Place model.bin or model.task in Downloads or app internal storage."
            _partialResults.tryEmit(msg)
            updateProgress(stage = LlmStage.ERROR, message = "Error: model file not found")
            isInitialized = false
            return
        }
        Log.d(TAG, "1/5 [initModel] validate_file: found → ${modelFile.absolutePath}")

        val fileSizeGB = modelFile.length().toDouble() / (1024 * 1024 * 1024)
        if (fileSizeGB > 5.0) {
            Log.w(TAG, "2/5 [initModel] size_check: TOO LARGE (${fileSizeGB}GB)")
            _partialResults.tryEmit("Error: Model file is invalid or corrupted (too large).")
            updateProgress(stage = LlmStage.ERROR, message = "Error: invalid model file")
            isInitialized = false
            return
        }

        initJob?.cancel()
        try { llmInference?.close() } catch (e: Exception) { Log.e(TAG, "release_old error", e) }
        llmInference = null
        isInitialized = false

        initJob = helperScope.launch {
            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(MAX_TOTAL_TOKENS)
                    .build()

                val inference = LlmInference.createFromOptions(context, options)

                if (!isActive) { inference?.close(); return@launch }

                if (inference == null) {
                    Log.w(TAG, "4/5 [initModel] model_load: createFromOptions returned null")
                    _partialResults.tryEmit("Error: MediaPipe model init returned null (unsupported format?)")
                    updateProgress(stage = LlmStage.ERROR, message = "Error: model init null")
                    isInitialized = false
                    return@launch
                }

                llmInference = inference
                isInitialized = true
                Log.i(TAG, "5/5 [initModel] init_complete: model ready")
                updateProgress(stage = LlmStage.IDLE, message = "Model ready")
                _partialResults.tryEmit("MediaPipe LLM model loaded successfully.")
            } catch (e: Exception) {
                if (!isActive) return@launch
                Log.e(TAG, "4/5 [initModel] model_load: FAILED", e)
                _partialResults.tryEmit("Error: Failed to initialize MediaPipe LLM model.\n${e.localizedMessage ?: "Unknown error"}")
                updateProgress(stage = LlmStage.ERROR, message = "Error: failed to initialize model")
                isInitialized = false
            }
        }
    }

    override fun reloadModel() = initModel()

    private fun isModelInitialized(): Boolean = isInitialized

    override fun generateResponse(prompt: String) {
        Log.d(TAG, "1/4 [generateResponse] called: promptLen=${prompt.length}")
        helperScope.launch {
            Log.d(TAG, "2/4 [generateResponse] init_wait: joining initJob")
            initJob?.join()

            Log.d(TAG, "3/4 [generateResponse] model_check: isInitialized=$isInitialized")
            val inference = llmInference
            if (!isInitialized || inference == null) {
                Log.e(TAG, "3/4 [generateResponse] model_check: NOT READY")
                _partialResults.tryEmit("\nError: Failed to initialize MediaPipe LLM model. Please select a valid .bin or .task file.")
                updateProgress(stage = LlmStage.ERROR, message = "Error: model not available")
                return@launch
            }

            Log.d(TAG, "4/4 [generateResponse] gen_start")
            generateResponseBySentence(
                inference = inference,
                prompt = prompt,
                onSentence = { sentence -> _partialResults.tryEmit(sentence + "\n") },
                onComplete = { updateProgress(stage = LlmStage.DONE, message = "Done") },
                onError = { error ->
                    Log.e(TAG, "4/4 [generateResponse] gen_start: ERROR", error)
                    _partialResults.tryEmit("Error: failed to generate response.")
                    updateProgress(stage = LlmStage.ERROR, message = "Error: failed to generate")
                }
            )
        }
    }

    override fun analyzeThoughtStructure(prompt: String): Flow<LlmStreamEvent> = flow {
        Log.d(TAG, "1/5 [analyzeThought] init_wait: joining initJob")
        initJob?.join()

        Log.d(TAG, "2/5 [analyzeThought] model_check: isInitialized=$isInitialized")
        val inference = llmInference
        if (!isInitialized || inference == null) {
            Log.w(TAG, "2/5 [analyzeThought] model_check: not ready")
            emit(LlmStreamEvent.Error("LLM model is not initialized. Please ensure a valid model file is present."))
            return@flow
        }

        Log.d(TAG, "3/5 [analyzeThought] prompt_trim: inputLen=${prompt.length}")
        val promptLimit = (MAX_TOTAL_TOKENS - OUTPUT_TOKENS_RESERVE).coerceAtLeast(1)
        val trimmedPrompt = trimPromptToTokenLimit(inference, prompt, promptLimit)

        Log.d(TAG, "4/5 [analyzeThought] generating")
        updateProgress(LlmStage.GENERATING, 0, 0, "Generating")
        try {
            val result = inference.generateResponse(trimmedPrompt)
            Log.d(TAG, "4/5 [analyzeThought] generating: got ${result.length} chars")
            emit(LlmStreamEvent.Delta(result))
            emit(LlmStreamEvent.Done(result))
            updateProgress(LlmStage.DONE, message = "Done")
        } catch (e: Exception) {
            Log.e(TAG, "4/5 [analyzeThought] generating: ERROR", e)
            updateProgress(LlmStage.ERROR, message = "Error: inference failed")
            emit(LlmStreamEvent.Error("Inference failed: ${e.localizedMessage ?: "Unknown error"}"))
        }
    }.flowOn(Dispatchers.IO)

    private fun close() {
        initJob?.cancel()
        initJob = null
        try { llmInference?.close() } catch (e: Exception) { Log.e(TAG, "close: error", e) }
        isInitialized = false
        llmInference = null
        updateProgress(stage = LlmStage.IDLE, current = 0, total = 0, message = "Idle")
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private fun resolveModelFile(): File? {
        val supportedExtensions = listOf("bin", "task")
        for (ext in supportedExtensions) {
            val f = File(context.filesDir, "model.$ext")
            if (f.exists() && f.length() > 0) {
                Log.i(TAG, "Found model in internal storage: ${f.absolutePath}")
                return f
            }
        }
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        for (ext in supportedExtensions) {
            val f = File(downloadsDir, "model.$ext")
            if (f.exists() && f.length() > 0) {
                Log.i(TAG, "Found model in Downloads: ${f.absolutePath}")
                return f
            }
        }
        return null
    }

    private fun generateResponseBySentence(
        inference: LlmInference,
        prompt: String,
        onSentence: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        updateProgress(stage = LlmStage.GENERATING, current = 0, total = 0, message = "Generating")
        _partialResults.tryEmit("\n--- MediaPipe LLM Response ---\n")
        val promptLimit = (MAX_TOTAL_TOKENS - OUTPUT_TOKENS_RESERVE).coerceAtLeast(1)
        val trimmedPrompt = trimPromptToTokenLimit(inference, prompt, promptLimit)

        try {
            val result = inference.generateResponse(trimmedPrompt)
            val sentences = splitIntoSentences(result)
            sentences.forEach(onSentence)
            onComplete()
        } catch (e: Exception) {
            Log.e(TAG, "generateResponseBySentence: ERROR", e)
            onError(e)
        }
    }

    private fun splitIntoSentences(text: String): List<String> {
        if (text.isBlank()) return listOf(text.trim())
        return SENTENCE_END_PATTERN.split(text)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(text.trim()) }
    }

    private fun trimPromptToTokenLimit(
        inference: LlmInference,
        prompt: String,
        maxPromptTokens: Int
    ): String {
        if (maxPromptTokens <= 0 || prompt.isEmpty()) return ""
        val fullTokens = inference.sizeInTokens(prompt)
        if (fullTokens <= maxPromptTokens) return prompt

        var low = 0
        var high = prompt.length
        var best = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            val tokens = inference.sizeInTokens(prompt.substring(0, mid))
            if (tokens <= maxPromptTokens) { best = mid; low = mid + 1 }
            else high = mid - 1
        }
        return if (best <= 0) "" else prompt.substring(0, best)
    }

    private fun updateProgress(
        stage: LlmStage? = null,
        current: Long? = null,
        total: Long? = null,
        message: String? = null
    ) {
        val prev = _progress.value
        _progress.value = InferenceProgress(
            stage = stage ?: prev.stage,
            current = current ?: prev.current,
            total = total?.let { if (it > 0L) it else 0L } ?: prev.total,
            message = message?.map { ch -> if (ch.code in 32..126) ch else '?' }?.joinToString("") ?: prev.message
        )
    }

    companion object {
        private const val TAG = "LLMInferenceServiceImpl"
        private const val MAX_TOTAL_TOKENS = 1024
        private const val OUTPUT_TOKENS_RESERVE = 256
        private val SENTENCE_END_PATTERN = Regex("(?<=[。！？.!?])")
    }
}
