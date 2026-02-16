package com.example.recemotion

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

class LLMInferenceHelper(val context: Context) {
    private var isInitialized = false
    private var modelType: ModelType = ModelType.UNKNOWN

    private val modelFilename = ModelDownloadHelper.MODEL_FILENAME
    private val _partialResults = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val partialResults: SharedFlow<String> = _partialResults.asSharedFlow()

    enum class ModelType {
        GGUF,      // llama.cpp format
        TFLITE,    // TensorFlow Lite format
        UNKNOWN
    }

    fun initModel() {
        val modelFile = resolveModelFile()
        if (modelFile == null) {
            Log.e(TAG, "Model file not found in Downloads or internal storage")
            _partialResults.tryEmit("Error: Model file not found. Place '${ModelDownloadHelper.MODEL_FILENAME}' in Downloads or app internal storage.")
            return
        }

        // ファイル拡張子で判定
        modelType = detectModelType(modelFile)
        
        when (modelType) {
            ModelType.GGUF -> initGGUFModel(modelFile)
            ModelType.TFLITE -> initTFLiteModel(modelFile)
            ModelType.UNKNOWN -> {
                Log.e(TAG, "Unknown model format: ${modelFile.extension}")
                _partialResults.tryEmit("Error: Unknown model format. Supported: .gguf, .tflite")
                isInitialized = false
            }
        }
    }

    fun isModelInitialized(): Boolean {
        return isInitialized
    }

    private fun detectModelType(modelFile: File): ModelType {
        return when (modelFile.extension.lowercase()) {
            "gguf" -> ModelType.GGUF
            "tflite", "bin" -> ModelType.TFLITE
            else -> ModelType.UNKNOWN
        }
    }

    private fun initGGUFModel(modelFile: File) {
        try {
            val nThreads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            val error = nativeInit(modelFile.absolutePath, DEFAULT_CTX, nThreads)
            if (error != null) {
                Log.e(TAG, "Failed to initialize GGUF model: $error")
                _partialResults.tryEmit("Error initializing GGUF model: $error")
                isInitialized = false
                return
            }
            isInitialized = true
            Log.i(TAG, "llama.cpp GGUF model initialized successfully")
            _partialResults.tryEmit("GGUF model loaded successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GGUF model", e)
            _partialResults.tryEmit("Error initializing GGUF model: ${e.message}")
            isInitialized = false
        }
    }

    private fun initTFLiteModel(modelFile: File) {
        try {
            val error = nativeInitTFLite(modelFile.absolutePath)
            if (error != null) {
                Log.e(TAG, "Failed to initialize TFLite model: $error")
                _partialResults.tryEmit("Error initializing TFLite model: $error")
                isInitialized = false
                return
            }
            isInitialized = true
            Log.i(TAG, "TensorFlow Lite model initialized successfully")
            _partialResults.tryEmit("TFLite model loaded successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TFLite model", e)
            _partialResults.tryEmit("Error initializing TFLite model: ${e.message}")
            isInitialized = false
        }
    }

    fun generateResponse(prompt: String) {
        Log.d(TAG, "=== generateResponse called ===")
        Log.d(TAG, "isInitialized: $isInitialized, modelType: $modelType")
        
        if (!isInitialized) {
            Log.w(TAG, "Model not initialized, attempting to initialize...")
            _partialResults.tryEmit("Model not initialized. Attempting to load...\n")
            initModel()
            if (!isInitialized) {
                Log.e(TAG, "Failed to initialize model")
                _partialResults.tryEmit("\nError: Failed to initialize model. Please select a valid GGUF file.")
                return
            }
        }

        Log.i(TAG, "Starting background thread for inference")
        Thread {
            try {
                Log.i(TAG, "Thread started - Starting inference with ${modelType.name} model")
                _partialResults.tryEmit("\n--- LLM Response ---\n")

                val effectivePrompt = if (DEBUG_SHORT_PROMPT) {
                    Log.w(TAG, "DEBUG_SHORT_PROMPT enabled: overriding input prompt")
                    DEBUG_PROMPT_TEXT
                } else {
                    prompt
                }
                val effectiveMaxTokens = if (DEBUG_SHORT_PROMPT) DEBUG_MAX_TOKENS else 32

                Log.d(TAG, "Prompt length: ${effectivePrompt.length} characters")
                Log.d(TAG, "Calling native generate with max_tokens=$effectiveMaxTokens...")
                
                val result = when (modelType) {
                    ModelType.GGUF -> {
                        Log.d(TAG, "Calling nativeGenerate...")
                        val startTime = System.currentTimeMillis()
                        
                        // Reduced max tokens for mobile (32 instead of 512)
                        val response = nativeGenerate(effectivePrompt, effectiveMaxTokens)
                        
                        val elapsedTime = System.currentTimeMillis() - startTime
                        Log.d(TAG, "nativeGenerate returned after ${elapsedTime}ms: ${response.take(100)}...")
                        
                        if (response.startsWith("Model is not initialized") || 
                            response.startsWith("Failed") || 
                            response.startsWith("Error")) {
                            Log.e(TAG, "Native generation error: $response")
                            "Error from llama.cpp:\n$response"
                        } else {
                            response
                        }
                    }
                    ModelType.TFLITE -> {
                        Log.d(TAG, "Calling nativeGenerateTFLite...")
                        val response = nativeGenerateTFLite(effectivePrompt, effectiveMaxTokens)
                        if (response.contains("not yet implemented")) {
                            Log.e(TAG, "TFLite not implemented: $response")
                            "Error: $response"
                        } else {
                            response
                        }
                    }
                    ModelType.UNKNOWN -> {
                        Log.e(TAG, "Model type is UNKNOWN")
                        "Error: Model type is unknown. Please reinitialize the model."
                    }
                }
                
                Log.i(TAG, "Emitting result to flow...")
                val emitted = _partialResults.tryEmit(result)
                Log.i(TAG, "Result emitted: $emitted")
                Log.i(TAG, "Inference completed: ${result.take(100)}...")
            } catch (e: Exception) {
                val errorMsg = "Error generating response:\n${e.message}\n\nStack trace:\n${e.stackTraceToString()}"
                Log.e(TAG, "Error generating response", e)
                _partialResults.tryEmit(errorMsg)
            }
        }.start()
        Log.d(TAG, "Background thread started")
    }

    fun close() {
        try {
            when (modelType) {
                ModelType.GGUF -> nativeRelease()
                ModelType.TFLITE -> nativeReleaseTFLite()
                ModelType.UNKNOWN -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing model", e)
        }
        isInitialized = false
        modelType = ModelType.UNKNOWN
    }

    private fun resolveModelFile(): File? {
        val supportedExtensions = listOf("gguf", "tflite", "bin")
        
        // 内部ストレージを優先的に検索
        for (ext in supportedExtensions) {
            val internalFile = File(context.filesDir, "model.$ext")
            if (internalFile.exists()) {
                Log.i(TAG, "Found model in internal storage: ${internalFile.absolutePath}")
                return internalFile
            }
        }

        // Downloads ディレクトリから検索
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        for (ext in supportedExtensions) {
            val downloadsFile = File(downloadsDir, "model.$ext")
            if (downloadsFile.exists()) {
                Log.i(TAG, "Found model in Downloads: ${downloadsFile.absolutePath}")
                return downloadsFile
            }
        }

        // 古い固定名もチェック（後方互換）
        val legacyFile = File(context.filesDir, modelFilename)
        if (legacyFile.exists()) {
            Log.i(TAG, "Found legacy model: ${legacyFile.absolutePath}")
            return legacyFile
        }

        return null
    }

    companion object {
        const val TAG = "LLMInferenceHelper"
        private const val DEFAULT_CTX = 2048
        private const val DEFAULT_MAX_TOKENS = 128
        private const val DEBUG_SHORT_PROMPT = true
        private const val DEBUG_PROMPT_TEXT = "Hello"
        private const val DEBUG_MAX_TOKENS = 8

        init {
            try {
                System.loadLibrary("llama_jni")
                Log.i(TAG, "✅ libllama_jni.so loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "❌ Failed to load libllama_jni.so: ${e.message}", e)
                throw e
            }
        }
    }

    // ========== GGUF (llama.cpp) JNI Methods ==========
    private external fun nativeInit(modelPath: String, nCtx: Int, nThreads: Int): String?
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String
    private external fun nativeRelease()

    // ========== TFLite JNI Methods ==========
    private external fun nativeInitTFLite(modelPath: String): String?
    private external fun nativeGenerateTFLite(prompt: String, maxTokens: Int): String
    private external fun nativeReleaseTFLite()
}