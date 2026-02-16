
package com.example.recemotion

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.recemotion.databinding.ActivityMainBinding
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.FileOutputStream
import java.util.Calendar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), FaceLandmarkerHelper.LandmarkerListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper
    private lateinit var cameraExecutor: ExecutorService
    
    // State
    private var wakeTimeUnix: Long = 0

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(this, "Permission request denied", Toast.LENGTH_SHORT).show()
            }
        }

    private val requestStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                checkAndDownloadModel()
            } else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private val openModelFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) {
                Log.i(TAG, "File selection cancelled")
                return@registerForActivityResult
            }
            
            // Show result console for feedback
            binding.scrollResult.visibility = View.VISIBLE
            binding.txtResult.text = "Loading model...\n"
            
            if (copyModelFromUri(uri)) {
                binding.txtResult.append("Model file copied successfully.\n")
                binding.txtResult.append("Initializing llama.cpp...\n")
                llmInferenceHelper.initModel()
                Toast.makeText(this, "Model imported and ready.", Toast.LENGTH_SHORT).show()
            } else {
                binding.txtResult.text = "Failed to import model.\n\n" +
                    "Please ensure:\n" +
                    "- File is a valid GGUF format\n" +
                    "- File is not corrupted\n" +
                    "- You have sufficient storage space"
                Toast.makeText(this, "Failed to import model.", Toast.LENGTH_LONG).show()
            }
        }

    private lateinit var llmInferenceHelper: LLMInferenceHelper
    private lateinit var modelDownloadHelper: ModelDownloadHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        faceLandmarkerHelper = FaceLandmarkerHelper(context = this, faceLandmarkerHelperListener = this)
        llmInferenceHelper = LLMInferenceHelper(this)
        modelDownloadHelper = ModelDownloadHelper(this)

        setupUI()
        
        // Default Wake Time: 7:00 AM Today
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 7)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        wakeTimeUnix = cal.timeInMillis / 1000
        
        // Init Session
        initSession(wakeTimeUnix)

        // Check for model and download if needed
        checkAndDownloadModel()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Collect LLM Results
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val fullResponse = StringBuilder()
                llmInferenceHelper.partialResults.collect { part ->
                    binding.txtResult.append(part)
                    fullResponse.append(part)
                    binding.scrollResult.post { 
                        binding.scrollResult.fullScroll(View.FOCUS_DOWN)
                    }
                    
                    // Simple heuristic to detect end: checks if part is empty or special token? 
                    // MediaPipe doesn't emit "Done" signal in this simple flow. 
                    // We'll log the partials or just log the Input in the button click for now.
                }
            }
        }
    }

    private fun checkAndDownloadModel() {
        if (needsStoragePermission() && !hasStoragePermission()) {
            requestStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            return
        }

        if (!modelDownloadHelper.isModelDownloaded()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("LLM Model Required")
                .setMessage("""
                          This app requires a GGUF model for llama.cpp.

                          Download Steps:
                          1. Download a GGUF model that fits your device.
                          2. Rename it to 'model.gguf'.
                          3. Move it to Downloads on your Android device:
                              /storage/emulated/0/Download/model.gguf

                          Alternative: You can still push to internal storage with:
                              adb push model.gguf /data/data/com.example.recemotion/files/model.gguf
                """.trimIndent())
                .setPositiveButton("Select File") { _, _ ->
                    openModelFileLauncher.launch(arrayOf("*/*"))
                }
                .setNeutralButton("I've Downloaded It") { _, _ ->
                    // Recheck if model exists
                    if (modelDownloadHelper.isModelDownloaded()) {
                        llmInferenceHelper.initModel()
                        Toast.makeText(this, "Model found! Ready to analyze.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Model not found. Please follow the instructions.", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Copy ADB Command") { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("ADB Command", 
                        "adb push model.gguf /data/data/com.example.recemotion/files/model.gguf")
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "Command copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
                .setCancelable(false)
                .show()
        } else {
            // Model exists, initialize LLM
            llmInferenceHelper.initModel()
        }
    }

    private fun needsStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    }

    private fun hasStoragePermission(): Boolean {
        if (!needsStoragePermission()) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ファイルの場所を要求するもの
    private fun copyModelFromUri(uri: Uri): Boolean {
        return try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)

            // ファイル名を取得
            val fileName = getFileName(uri)
            Log.i(TAG, "Selected file: $fileName")
            
            // 拡張子をチェック
            if (!isSupportedModelFormat(fileName)) {
                val errorMsg = "Unsupported format: $fileName. Supported: .gguf, .tflite, .bin"
                Log.e(TAG, errorMsg)
                runOnUiThread {
                    binding.txtResult.text = "Error: $errorMsg\n\n" +
                        "For llama.cpp, please select a .gguf file."
                }
                Toast.makeText(this, "Unsupported format. Supported: .gguf, .tflite, .bin", Toast.LENGTH_SHORT).show()
                return false
            }

            // 拡張子を保持して固定名にリネーム（内部で複数モデル管理する場合は別途対応）
            val extension = fileName.substringAfterLast(".")
            val targetFileName = "model.$extension"  // model.gguf, model.tflite など
            val targetFile = java.io.File(filesDir, targetFileName)
            
            Log.i(TAG, "Copying to: ${targetFile.absolutePath}")
            
            contentResolver.openInputStream(uri).use { inputStream ->
                if (inputStream == null) {
                    Log.e(TAG, "Failed to open input stream")
                    return false
                }
                FileOutputStream(targetFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }
                    Log.i(TAG, "Copied $totalBytes bytes")
                }
            }
            Log.i(TAG, "✅ Model copied successfully to: ${targetFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to copy model file", e)
            runOnUiThread {
                binding.txtResult.text = "Error copying model file:\n${e.message}\n\n" +
                    "Stack trace:\n${e.stackTraceToString()}"
            }
            false
        }
    }

    private fun getFileName(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst()) {
                it.getString(index)
            } else {
                ModelDownloadHelper.MODEL_FILENAME
            }
        } ?: ModelDownloadHelper.MODEL_FILENAME
    }

    private fun isSupportedModelFormat(fileName: String): Boolean {
        val extension = fileName.substringAfterLast(".").lowercase()
        return extension in listOf("gguf", "tflite", "bin")
    }


    
    private fun logToHistory(jsonStr: String) {
        try {
            val file = java.io.File(getExternalFilesDir(null), "emotion_log.jsonl")
            val writer = java.io.FileWriter(file, true)
            writer.append(jsonStr + "\n")
            writer.close()
            Log.i(TAG, "Logged to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log", e)
        }
    }

    private fun setupUI() {
        // Wake Time Picker
        binding.btnSetWakeTime.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(this, { _, hour, minute ->
                val newCal = Calendar.getInstance()
                newCal.set(Calendar.HOUR_OF_DAY, hour)
                newCal.set(Calendar.MINUTE, minute)
                wakeTimeUnix = newCal.timeInMillis / 1000
                
                binding.txtWakeTime.text = String.format("%02d:%02d", hour, minute)
                initSession(wakeTimeUnix) // Reset session with new time
                Toast.makeText(this, "Session Reset", Toast.LENGTH_SHORT).show()
                
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }
        
        // Stress Slider
        binding.sliderStress.addOnChangeListener { _, value, _ ->
            updateStressLevel(value.toInt())
            binding.txtStats.text = binding.txtStats.text.toString().replace(Regex("STRESS: \\d"), "STRESS: ${value.toInt()}")
        }
        
        // Reset/Calibrate Button
        binding.btnReset.setOnClickListener {
            initSession(wakeTimeUnix)
            // No toast needed, overlay will appear
        }

        // Select Model Button
        binding.btnSelectModel.setOnClickListener {
            openModelFileLauncher.launch(arrayOf("*/*"))
        }

        // Analyze Button
        binding.btnAnalyze.setOnClickListener {
            Log.d(TAG, "=== ANALYZE button clicked ===")
            val text = binding.edtReflection.text.toString()
            if (text.isEmpty()) {
                Toast.makeText(this, "Please write a reflection first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show Result View
            binding.scrollResult.visibility = View.VISIBLE
            binding.txtResult.text = "Analyzing...\n"
            Log.d(TAG, "Result view shown")
            
            // Check if model is initialized
            val modelInitialized = llmInferenceHelper.isModelInitialized()
            Log.d(TAG, "Model initialized: $modelInitialized")
            
            if (!modelInitialized) {
                binding.txtResult.text = "Error: LLM model is not initialized.\n\n" +
                    "Please select a GGUF model file using the 'SELECT MODEL' button.\n\n" +
                    "You can download GGUF models from:\n" +
                    "- Hugging Face (search for 'gguf')\n" +
                    "- llama.cpp compatible models\n\n" +
                    "Recommended: Small models (< 1GB) for mobile devices."
                Log.e(TAG, "Analysis failed: Model not initialized")
                return@setOnClickListener
            }
            
            try {
                // Generate Prompt JSON
                Log.d(TAG, "Generating analysis JSON...")
                val jsonContext = getAnalysisJson(text)
                Log.d(TAG, "JSON generated: ${jsonContext.take(200)}...")
                
                // Log Input
                logToHistory(jsonContext)
                
                // Construct Prompt for LLM
                val prompt = """
                    You are an expert psychological counselor. Analyze the user's emotion data and reflection.
                    
                    Methodology:
                    1. Detect discrepancies between stated emotion (text) and physical emotion (face).
                    2. Consider physical state (Energy, Stress) as factors.
                    3. Ask a probing question to deepen insight.
                    
                    Input Data (JSON):
                    $jsonContext
                    
                    Response (Keep it short, empathetic, and insightful):
                """.trimIndent()

                Log.d(TAG, "Calling llmInferenceHelper.generateResponse...")
                llmInferenceHelper.generateResponse(prompt)
                Log.d(TAG, "generateResponse call completed")
                
            } catch (e: Exception) {
                binding.txtResult.text = "Error during analysis:\n${e.message}\n\n" +
                    "Stack trace:\n${e.stackTraceToString()}"
                Log.e(TAG, "Analysis failed with exception", e)
            }

            // Hide keyboard
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        faceLandmarkerHelper.detectLiveStream(imageProxy, isFrontCamera = true)
                        imageProxy.close()
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResults(result: FaceLandmarkerResult, inferenceTime: Long) {
        if (result.faceLandmarks().isNotEmpty()) {
            val firstFaceLandmarks = result.faceLandmarks()[0]
            
            // Flatten landmarks
            val flattened = FloatArray(firstFaceLandmarks.size * 3)
            for (i in firstFaceLandmarks.indices) {
                val point = firstFaceLandmarks[i]
                flattened[i * 3] = point.x()
                flattened[i * 3 + 1] = point.y()
                flattened[i * 3 + 2] = point.z()
            }

            // Send to Rust JNI
            pushFaceLandmarks(flattened)
            
            // Get JSON (only for realtime stats, empty text)
            // Ideally we only call this periodically if it's heavy, but Rust is fast.
            // We pass empty string because we just want updates on stats.
            val jsonStr = getAnalysisJson("")
            
            runOnUiThread {
                updateUI(jsonStr)
            }
        }
    }

    private fun updateUI(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val context = json.getJSONObject("context")
            val emotionData = json.getJSONObject("emotion_data")
            
            val isCalibrated = emotionData.optBoolean("is_calibrated", false)
            val currentEmotion = emotionData.optString("current_emotion", "Neutral")
            val energy = context.optInt("energy_level", 3)
            val stress = context.optInt("stress_level", 1)

            // 1. Handle Calibration Overlay
            if (isCalibrated) {
                if (binding.overlayCalibration.visibility == View.VISIBLE) {
                     binding.overlayCalibration.visibility = View.GONE
                }
            } else {
                if (binding.overlayCalibration.visibility == View.GONE) {
                     binding.overlayCalibration.visibility = View.VISIBLE
                }
                return // Don't allow stats update if calibrating
            }

            // 2. Update Emotion Text (Monochrome: Always White)
            binding.txtEmotion.text = currentEmotion.uppercase()
            // Kept white text color from XML

            // 3. Update Stats
            binding.txtStats.text = "ENERGY: $energy | STRESS: $stress"

        } catch (e: Exception) {
            Log.e(TAG, "JSON Parse Error: ${e.message}")
        }
    }

    override fun onError(error: String, errorCode: Int) {
        Log.e(TAG, "FaceLandmarker Error: $error")
        runOnUiThread {
            Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        faceLandmarkerHelper.clearFaceLandmarker()
        llmInferenceHelper.close()
    }
    
    override fun onEmpty() {
        // Optional
    }

    companion object {
        const val TAG = "RecEmotion_Main"

        init {
            try {
                System.loadLibrary("recemotion")
                Log.i(TAG, "✅ librecemotion.so (Rust) loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "❌ Failed to load librecemotion.so: ${e.message}", e)
                throw e
            }
        }

        // JNI Bridge Functions
        @JvmStatic
        external fun initSession(wakeTime: Long)

        @JvmStatic
        external fun pushFaceLandmarks(landmarks: FloatArray)

        @JvmStatic
        external fun getAnalysisJson(text: String): String

        @JvmStatic
        external fun updateStressLevel(level: Int)
    }
}
