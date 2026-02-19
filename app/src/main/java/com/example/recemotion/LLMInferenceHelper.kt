package com.example.recemotion

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.example.recemotion.data.llm.LlmStreamEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class LLMInferenceHelper(val context: Context) {
    @Volatile private var isInitialized = false
    @Volatile private var llmInference: LlmInference? = null

    private val helperScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var initJob: Job? = null

    private val _partialResults = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val partialResults: SharedFlow<String> = _partialResults.asSharedFlow()

    private val _progress = MutableStateFlow(
        InferenceProgress(stage = Stage.IDLE, current = 0, total = 0, message = "Idle")
    )
    val progress: StateFlow<InferenceProgress> = _progress.asStateFlow()

    enum class Stage { IDLE, LOADING, GENERATING, DONE, ERROR }

    // ─────────────────────────────────────────────────────────────────────────
    // 公開 API
    // ─────────────────────────────────────────────────────────────────────────

    fun initModel() {
        // 1/5: ファイル検証（UIスレッドで実行・軽量）
        Log.d(TAG, "1/5 [initModel] validate_file: searching for model file")
        updateProgress(stage = Stage.LOADING, current = 0, total = 0, message = "Loading model")

        val modelFile = resolveModelFile()
        if (modelFile == null) {
            Log.e(TAG, "1/5 [initModel] validate_file: NOT FOUND")
            val msg = "Error: Model file not found. Place model.bin or model.task in Downloads or app internal storage."
            _partialResults.tryEmit(msg)
            updateProgress(stage = Stage.ERROR, message = "Error: model file not found")
            isInitialized = false
            return
        }
        Log.d(TAG, "1/5 [initModel] validate_file: found → ${modelFile.absolutePath}")

        // 2/5: サイズ検証
        Log.d(TAG, "2/5 [initModel] size_check: ${modelFile.length()} bytes")
        val fileSizeGB = modelFile.length().toDouble() / (1024 * 1024 * 1024)
        if (fileSizeGB > 5.0) {
            Log.w(TAG, "2/5 [initModel] size_check: TOO LARGE (${fileSizeGB}GB)")
            _partialResults.tryEmit("Error: Model file is invalid or corrupted (too large).")
            updateProgress(stage = Stage.ERROR, message = "Error: invalid model file")
            isInitialized = false
            return
        }

        // 3/5: 旧モデル解放
        Log.d(TAG, "3/5 [initModel] release_old: cancelling previous job and releasing model")
        initJob?.cancel()
        try { llmInference?.close() } catch (e: Exception) { Log.e(TAG, "3/5 [initModel] release_old: close error", e) }
        llmInference = null
        isInitialized = false

        // 4/5: IOスレッドでモデルロード（ブロッキング・ANR回避）
        Log.d(TAG, "4/5 [initModel] model_load: launching IO coroutine")
        initJob = helperScope.launch {
            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(MAX_TOTAL_TOKENS)
                    .build()

                val inference = LlmInference.createFromOptions(context, options)

                if (!isActive) {
                    inference?.close()
                    return@launch
                }

                if (inference == null) {
                    Log.w(TAG, "4/5 [initModel] model_load: createFromOptions returned null")
                    updateProgress(stage = Stage.ERROR, message = "Error: model init returned null")
                    isInitialized = false
                    return@launch
                }

                llmInference = inference
                isInitialized = true
                // 5/5: 完了
                Log.i(TAG, "5/5 [initModel] init_complete: model ready")
                updateProgress(stage = Stage.IDLE, message = "Model ready")
                _partialResults.tryEmit("MediaPipe LLM model loaded successfully.")
            } catch (e: Exception) {
                if (!isActive) return@launch
                Log.e(TAG, "4/5 [initModel] model_load: FAILED", e)
                _partialResults.tryEmit("Error: failed to initialize MediaPipe LLM model.")
                updateProgress(stage = Stage.ERROR, message = "Error: failed to initialize model")
                isInitialized = false
            }
        }
    }

    fun isModelInitialized(): Boolean = isInitialized

    /**
     * テキスト生成（文単位コールバック版）。
     * helperScope (Dispatchers.IO) で実行するため UI スレッドをブロックしない。
     * generateResponseAsync のネイティブコールバックスレッドは JVM 未アタッチで
     * ProgressListener が届かないため、同期版 generateResponse を使用する。
     */
    fun generateResponse(prompt: String) {
        Log.d(TAG, "1/4 [generateResponse] called: promptLen=${prompt.length}")
        helperScope.launch {
            // 2/4: モデルロード完了を待機（既に完了済みなら即通過）
            Log.d(TAG, "2/4 [generateResponse] init_wait: joining initJob")
            initJob?.join()

            // 3/4: モデル状態確認
            Log.d(TAG, "3/4 [generateResponse] model_check: isInitialized=$isInitialized")
            val inference = llmInference
            if (!isInitialized || inference == null) {
                Log.e(TAG, "3/4 [generateResponse] model_check: NOT READY")
                _partialResults.tryEmit("\nError: Failed to initialize MediaPipe LLM model. Please select a valid .bin or .task file.")
                updateProgress(stage = Stage.ERROR, message = "Error: model not available")
                return@launch
            }

            // 4/4: 推論実行（IO スレッド上で同期呼び出し）
            Log.d(TAG, "4/4 [generateResponse] gen_start: calling generateResponseBySentence")
            generateResponseBySentence(
                inference = inference,
                prompt = prompt,
                onSentence = { sentence -> _partialResults.tryEmit(sentence + "\n") },
                onComplete = { updateProgress(stage = Stage.DONE, message = "Done") },
                onError = { error ->
                    Log.e(TAG, "4/4 [generateResponse] gen_start: ERROR", error)
                    _partialResults.tryEmit("Error: failed to generate response.")
                    updateProgress(stage = Stage.ERROR, message = "Error: failed to generate")
                }
            )
        }
    }

    /**
     * 思考構造解析（Flow 版）。
     * flow + flowOn(Dispatchers.IO) で全処理を IO スレッドに閉じ込める。
     * generateResponseAsync のコールバックは JVM 未アタッチのネイティブスレッドから
     * 呼ばれるため ProgressListener が届かない。同期版 generateResponse で回避する。
     */
    fun analyzeThoughtStructure(structureText: String): Flow<LlmStreamEvent> = flow {
        // 1/5: モデルロード完了を待機
        Log.d(TAG, "1/5 [analyzeThought] init_wait: joining initJob")
        initJob?.join()

        // 2/5: モデル状態確認
        Log.d(TAG, "2/5 [analyzeThought] model_check: isInitialized=$isInitialized")
        val inference = llmInference
        if (!isInitialized || inference == null) {
            Log.w(TAG, "2/5 [analyzeThought] model_check: not ready → TestLLMInference")
            TestLLMInference.analyzeThoughtStructure(structureText).collect { emit(it) }
            return@flow
        }

        // 3/5: プロンプトをトークン上限に収める（sizeInTokens は同期・推論前に完結）
        Log.d(TAG, "3/5 [analyzeThought] prompt_trim: inputLen=${structureText.length}")
        val promptLimit = (MAX_TOTAL_TOKENS - OUTPUT_TOKENS_RESERVE).coerceAtLeast(1)
        val trimmedPrompt = trimPromptToTokenLimit(inference, structureText, promptLimit)
        Log.d(TAG, "3/5 [analyzeThought] prompt_trim: trimmedLen=${trimmedPrompt.length}")

        // 4/5: 同期推論（IO スレッド上で実行・JVM 未アタッチ問題を回避）
        Log.d(TAG, "4/5 [analyzeThought] generating: calling sync generateResponse")
        updateProgress(Stage.GENERATING, 0, 0, "Generating")
        try {
            val result = inference.generateResponse(trimmedPrompt)
            Log.d(TAG, "4/5 [analyzeThought] generating: got ${result.length} chars")
            emit(LlmStreamEvent.Delta(result))

            // 5/5: 完了
            Log.d(TAG, "5/5 [analyzeThought] done")
            emit(LlmStreamEvent.Done(result))
            updateProgress(Stage.DONE, message = "Done")
        } catch (e: Exception) {
            Log.e(TAG, "4/5 [analyzeThought] generating: ERROR → TestLLMInference", e)
            updateProgress(Stage.ERROR, message = "Error: inference failed")
            TestLLMInference.analyzeThoughtStructure(structureText).collect { emit(it) }
        }
    }.flowOn(Dispatchers.IO) // flow ブロック全体を IO スレッドで実行

    fun close() {
        initJob?.cancel()
        initJob = null
        try { llmInference?.close() } catch (e: Exception) { Log.e(TAG, "close: error", e) }
        isInitialized = false
        llmInference = null
        updateProgress(stage = Stage.IDLE, current = 0, total = 0, message = "Idle")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 内部実装
    // ─────────────────────────────────────────────────────────────────────────

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

    /**
     * 同期推論で全文を取得し、文単位に分割してコールバックする。
     * 呼び出し元は helperScope (Dispatchers.IO) のコルーチン内なので
     * ブロッキング呼び出しは問題ない。
     */
    private fun generateResponseBySentence(
        inference: LlmInference,
        prompt: String,
        onSentence: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        // 1/3: プロンプトトリム（sizeInTokens は同期・推論前に完結）
        Log.d(TAG, "  1/3 [genBySentence] prompt_trim: computing limit")
        updateProgress(stage = Stage.GENERATING, current = 0, total = 0, message = "Generating")
        _partialResults.tryEmit("\n--- MediaPipe LLM Response ---\n")
        val promptLimit = (MAX_TOTAL_TOKENS - OUTPUT_TOKENS_RESERVE).coerceAtLeast(1)
        val trimmedPrompt = trimPromptToTokenLimit(inference, prompt, promptLimit)
        Log.d(TAG, "  1/3 [genBySentence] prompt_trim: trimmedLen=${trimmedPrompt.length}")

        // 2/3: 同期推論
        Log.d(TAG, "  2/3 [genBySentence] generating: calling sync generateResponse")
        try {
            val result = inference.generateResponse(trimmedPrompt)
            Log.d(TAG, "  2/3 [genBySentence] generating: got ${result.length} chars")

            val sentences = splitIntoSentences(result)
            // 3/3: 文単位コールバック
            Log.d(TAG, "  3/3 [genBySentence] done: emitting ${sentences.size} sentences")
            sentences.forEach(onSentence)
            onComplete()
        } catch (e: Exception) {
            Log.e(TAG, "  2/3 [genBySentence] generating: ERROR", e)
            onError(e)
        }
    }

    /** SENTENCE_END_PATTERN（ゼロ幅lookbehind）でsplitすると句点付きで分割される */
    private fun splitIntoSentences(text: String): List<String> {
        if (text.isBlank()) return listOf(text.trim())
        return SENTENCE_END_PATTERN.split(text)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(text.trim()) }
    }

    /**
     * バイナリサーチでトークン上限以内に収まる最長プロンプトを返す。
     * sizeInTokens は同期呼び出しのため、generateResponse/generateResponseAsync
     * より前に完結させること（セッション競合を避けるため）。
     */
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
        stage: Stage? = null,
        current: Long? = null,
        total: Long? = null,
        message: String? = null
    ) {
        val prev = _progress.value
        _progress.value = InferenceProgress(
            stage = stage ?: prev.stage,
            current = current ?: prev.current,
            total = total?.let { if (it > 0L) it else 0L } ?: prev.total,
            message = message?.let { sanitizeAscii(it) } ?: prev.message
        )
    }

    private fun sanitizeAscii(message: String): String =
        message.map { ch -> if (ch.code in 32..126) ch else '?' }.joinToString("")

    companion object {
        const val TAG = "LLMInferenceHelper"
        private const val MAX_TOTAL_TOKENS = 1024
        private const val OUTPUT_TOKENS_RESERVE = 256
        private val SENTENCE_END_PATTERN = Regex("(?<=[。！？.!?])")
    }
}

data class InferenceProgress(
    val stage: LLMInferenceHelper.Stage,
    val current: Long,
    val total: Long,
    val message: String
)
