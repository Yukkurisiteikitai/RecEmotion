
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
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.recemotion.databinding.ActivityMainBinding
import com.example.recemotion.data.db.AppDatabase
import com.example.recemotion.data.llm.ThoughtAnalysisJsonParser
import com.example.recemotion.data.llm.ThoughtPromptBuilder
import com.example.recemotion.data.parser.CabochaDependencyParser
import com.example.recemotion.data.parser.CabochaThoughtMapper
import com.example.recemotion.data.repository.ThoughtRepository
import com.example.recemotion.data.serialization.ThoughtStructureJsonAdapter
import com.example.recemotion.domain.usecase.AnalyzeThoughtUseCase
import com.example.recemotion.presentation.ThoughtAnalysisViewModel
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
                binding.txtResult.append("Initializing MediaPipe LLM...\n")
                llmInferenceHelper.initModel()
                Toast.makeText(this, "Model imported and ready.", Toast.LENGTH_SHORT).show()
            } else {
                binding.txtResult.text = "Failed to import model.\n\n" +
                    "Please ensure:\n" +
                    "- File is a valid MediaPipe LLM model (.bin or .task)\n" +
                    "- File is not corrupted\n" +
                    "- You have sufficient storage space"
                Toast.makeText(this, "Failed to import model.", Toast.LENGTH_LONG).show()
            }
        }

    private lateinit var llmInferenceHelper: LLMInferenceHelper
    private lateinit var modelDownloadHelper: ModelDownloadHelper
    private lateinit var thoughtAnalysisViewModel: ThoughtAnalysisViewModel
    private lateinit var gestureDetector: GestureDetector

    private enum class Screen {
        MAIN,
        CALENDAR
    }

    private var currentScreen: Screen = Screen.CALENDAR
    private var cachedResultVisibility: Int = View.GONE
    private var cachedProgressVisibility: Int = View.GONE
    private var cachedCalibrationVisibility: Int = View.GONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        
        supportActionBar?.hide()
        cameraExecutor = Executors.newSingleThreadExecutor()
        faceLandmarkerHelper = FaceLandmarkerHelper(context = this, faceLandmarkerHelperListener = this)
        llmInferenceHelper = LLMInferenceHelper(this)
        modelDownloadHelper = ModelDownloadHelper(this)
        thoughtAnalysisViewModel = createThoughtAnalysisViewModel()

        setupSwipeGesture()
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

        // Collect LLM Progress
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                llmInferenceHelper.progress.collect { progress ->
                    val isActive = progress.stage == LLMInferenceHelper.Stage.LOADING ||
                        progress.stage == LLMInferenceHelper.Stage.GENERATING
                    binding.progressContainer.visibility = if (isActive) View.VISIBLE else View.GONE

                    val total = progress.total
                    if (total > 0L) {
                        binding.progressBar.isIndeterminate = false
                        val maxValue = total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        val currentValue = progress.current
                            .coerceAtMost(total)
                            .coerceAtMost(Int.MAX_VALUE.toLong())
                            .toInt()
                        binding.progressBar.max = maxValue
                        binding.progressBar.progress = currentValue
                    } else {
                        binding.progressBar.isIndeterminate = true
                    }
                }
            }
        }

        // Collect Thought Structuring UI State
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                thoughtAnalysisViewModel.uiState.collect { state ->
                    if (state.isAnalyzing) {
                        binding.scrollResult.visibility = View.VISIBLE
                        binding.progressContainer.visibility = View.VISIBLE
                    }

                    state.error?.let { error ->
                        binding.scrollResult.visibility = View.VISIBLE
                        binding.progressContainer.visibility = View.GONE
                        binding.txtResult.text = "Error: $error"
                    }

                    if (state.partialStreamingText.isNotBlank()) {
                        binding.scrollResult.visibility = View.VISIBLE
                        binding.txtResult.text = state.partialStreamingText
                        binding.scrollResult.post {
                            binding.scrollResult.fullScroll(View.FOCUS_DOWN)
                        }
                    }

                    state.finalResult?.let { result ->
                        binding.progressContainer.visibility = View.GONE
                        val summary = buildString {
                            append("\n--- Thought Analysis ---\n")
                            append("Premises: ").append(result.premises.joinToString())
                            append("\nEmotions: ").append(result.emotions.joinToString())
                            append("\nInferences: ").append(result.inferences.joinToString())
                            append("\nPossible Biases: ")
                            append(result.possibleBiases.joinToString { it.name })
                            append("\nMissing Perspectives: ")
                            append(result.missingPerspectives.joinToString { it.description })
                        }
                        binding.txtResult.append(summary)
                    }
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
                          This app requires a MediaPipe LLM model.

                          Download Steps:
                          1. Download a MediaPipe LLM model that fits your device.
                          2. Rename it to 'model.task' or 'model.bin'.
                          3. Move it to Downloads on your Android device:
                              /storage/emulated/0/Download/model.task
                              (or /storage/emulated/0/Download/model.bin)

                          Alternative: You can still push to internal storage with:
                              adb push model.task /data/data/com.example.recemotion/files/model.task
                              (or adb push model.bin /data/data/com.example.recemotion/files/model.bin)
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
                        "adb push model.task /data/data/com.example.recemotion/files/model.task")
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
                val errorMsg = "Unsupported format: $fileName. Supported: .bin, .task"
                Log.e(TAG, errorMsg)
                runOnUiThread {
                    binding.txtResult.text = "Error: $errorMsg\n\n" +
                        "For MediaPipe LLM, please select a .bin or .task file."
                }
                Toast.makeText(this, "Unsupported format. Supported: .bin, .task", Toast.LENGTH_SHORT).show()
                return false
            }

            // 拡張子を保持して固定名にリネーム（内部で複数モデル管理する場合は別途対応）
            val extension = fileName.substringAfterLast(".")
            val targetFileName = "model.$extension"  // model.bin, model.task
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
        return extension in listOf("bin", "task")
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
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_main -> setScreen(Screen.MAIN)
                R.id.menu_calendar -> setScreen(Screen.CALENDAR)
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        binding.navView.setCheckedItem(R.id.menu_main)
        setScreen(Screen.MAIN)

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
            thoughtAnalysisViewModel.analyze(text)

            // Hide keyboard
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        }
    }

    private fun setupSwipeGesture() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val diffX = e2.x - (e1?.x ?: 0f)
                val diffY = e2.y - (e1?.y ?: 0f)
                
                // 横方向のスワイプを優先
                if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY)) {
                    // 左から右へのスワイプ
                    if (diffX > 100 && kotlin.math.abs(velocityX) > 100) {
                        binding.drawerLayout.openDrawer(GravityCompat.START)
                        return true
                    }
                }
                return false
            }
        })

        // メインコンテンツ全体にタッチリスナーを設定
        binding.mainContent.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false // 他のタッチイベントも処理させる
        }
    }

    private fun setScreen(screen: Screen) {
        if (currentScreen == screen) return

        currentScreen = screen
        val isCalendar = screen == Screen.CALENDAR

        if (isCalendar) {
            cachedResultVisibility = binding.scrollResult.visibility
            cachedProgressVisibility = binding.progressContainer.visibility
            cachedCalibrationVisibility = binding.overlayCalibration.visibility
        }

        binding.cardCalendar.visibility = if (isCalendar) View.VISIBLE else View.GONE
        binding.viewFinder.visibility = if (isCalendar) View.GONE else View.VISIBLE
        binding.cardTopInfo.visibility = if (isCalendar) View.GONE else View.VISIBLE
        binding.cardControls.visibility = if (isCalendar) View.GONE else View.VISIBLE

        if (isCalendar) {
            binding.scrollResult.visibility = View.GONE
            binding.progressContainer.visibility = View.GONE
            binding.overlayCalibration.visibility = View.GONE
        } else {
            binding.scrollResult.visibility = cachedResultVisibility
            binding.progressContainer.visibility = cachedProgressVisibility
            binding.overlayCalibration.visibility = cachedCalibrationVisibility
        }
    }

    private fun createThoughtAnalysisViewModel(): ThoughtAnalysisViewModel {
        val db = AppDatabase.getInstance(this)
        val repository = ThoughtRepository(db.thoughtEntryDao(), db.thoughtAnalysisDao())
        val useCase = AnalyzeThoughtUseCase(
            parser = CabochaDependencyParser(),
            mapper = CabochaThoughtMapper(),
            promptBuilder = ThoughtPromptBuilder(),
            llmHelper = llmInferenceHelper,
            jsonParser = ThoughtAnalysisJsonParser(),
            repository = repository,
            serializer = ThoughtStructureJsonAdapter()
        )

        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(ThoughtAnalysisViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return ThoughtAnalysisViewModel(useCase) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }

        return ViewModelProvider(this, factory)[ThoughtAnalysisViewModel::class.java]
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
