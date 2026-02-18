package com.example.recemotion

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.recemotion.data.db.AppDatabase
import com.example.recemotion.data.llm.ThoughtAnalysisJsonParser
import com.example.recemotion.data.llm.ThoughtPromptBuilder
import com.example.recemotion.data.parser.CabochaDependencyParser
import com.example.recemotion.data.parser.CabochaThoughtMapper
import com.example.recemotion.data.repository.ThoughtRepository
import com.example.recemotion.data.serialization.ThoughtStructureJsonAdapter
import com.example.recemotion.databinding.FragmentMainScreenBinding
import com.example.recemotion.domain.usecase.AnalyzeThoughtUseCase
import com.example.recemotion.presentation.ThoughtAnalysisViewModel
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.FileOutputStream
import java.util.Calendar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * MAIN画面のFragment。
 * カメラ・顔感情検出・LLM解析・コントロールUIを担当する。
 */
class MainScreenFragment : Fragment(), FaceLandmarkerHelper.LandmarkerListener {

    private var _binding: FragmentMainScreenBinding? = null
    private val binding get() = _binding!!

    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var llmInferenceHelper: LLMInferenceHelper
    private lateinit var modelDownloadHelper: ModelDownloadHelper
    private lateinit var thoughtAnalysisViewModel: ThoughtAnalysisViewModel

    private var wakeTimeUnix: Long = 0

    // --- Activity Result Launchers ---

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) startCamera()
            else Toast.makeText(requireContext(), "Permission request denied", Toast.LENGTH_SHORT).show()
        }

    private val requestStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) checkAndDownloadModel()
            else Toast.makeText(requireContext(), "Storage permission denied", Toast.LENGTH_SHORT).show()
        }

    private val openModelFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult

            binding.scrollResult.visibility = View.VISIBLE
            binding.txtResult.text = "Loading model...\n"

            if (copyModelFromUri(uri)) {
                binding.txtResult.append("Model file copied successfully.\n")
                binding.txtResult.append("Initializing MediaPipe LLM...\n")
                llmInferenceHelper.initModel()
                Toast.makeText(requireContext(), "Model imported and ready.", Toast.LENGTH_SHORT).show()
            } else {
                binding.txtResult.text = "Failed to import model.\n\n" +
                    "Please ensure:\n" +
                    "- File is a valid MediaPipe LLM model (.bin or .task)\n" +
                    "- File is not corrupted\n" +
                    "- You have sufficient storage space"
                Toast.makeText(requireContext(), "Failed to import model.", Toast.LENGTH_LONG).show()
            }
        }

    // --- Fragment Lifecycle ---

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        faceLandmarkerHelper = FaceLandmarkerHelper(context = requireContext(), faceLandmarkerHelperListener = this)
        llmInferenceHelper = LLMInferenceHelper(requireContext())
        modelDownloadHelper = ModelDownloadHelper(requireContext())
        thoughtAnalysisViewModel = createThoughtAnalysisViewModel()

        setupUI()

        // デフォルト起床時刻: 今日の 7:00 AM
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 7)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        wakeTimeUnix = cal.timeInMillis / 1000
        MainActivity.initSession(wakeTimeUnix)

        checkAndDownloadModel()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        collectLlmResults()
        collectLlmProgress()
        collectThoughtAnalysisState()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) stopCamera() else startCamera()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        faceLandmarkerHelper.clearFaceLandmarker()
        llmInferenceHelper.close()
        _binding = null
    }

    // --- UI Setup ---

    private fun setupUI() {
        // 起床時刻ピッカー
        binding.btnSetWakeTime.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(requireContext(), { _, hour, minute ->
                val newCal = Calendar.getInstance()
                newCal.set(Calendar.HOUR_OF_DAY, hour)
                newCal.set(Calendar.MINUTE, minute)
                wakeTimeUnix = newCal.timeInMillis / 1000

                binding.txtWakeTime.text = String.format("%02d:%02d", hour, minute)
                MainActivity.initSession(wakeTimeUnix)
                Toast.makeText(requireContext(), "Session Reset", Toast.LENGTH_SHORT).show()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }

        // ストレスレベルスライダー
        binding.sliderStress.addOnChangeListener { _, value, _ ->
            MainActivity.updateStressLevel(value.toInt())
            binding.txtStats.text = binding.txtStats.text.toString()
                .replace(Regex("STRESS: \\d+"), "STRESS: ${value.toInt()}")
        }

        // 再キャリブレーションボタン
        binding.btnReset.setOnClickListener {
            MainActivity.initSession(wakeTimeUnix)
        }

        // モデル選択ボタン
        binding.btnSelectModel.setOnClickListener {
            openModelFileLauncher.launch(arrayOf("*/*"))
        }

        // 解析ボタン
        binding.btnAnalyze.setOnClickListener {
            val text = binding.edtReflection.text.toString()
            if (text.isEmpty()) {
                Toast.makeText(requireContext(), "Please write a reflection first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.scrollResult.visibility = View.VISIBLE
            binding.txtResult.text = "Analyzing...\n"
            thoughtAnalysisViewModel.analyze(text)

            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        }
    }

    // --- Camera Control ---

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

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

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun stopCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProviderFuture.get().unbindAll()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    // --- Face Landmark Callbacks ---

    override fun onResults(result: FaceLandmarkerResult, inferenceTime: Long) {
        if (result.faceLandmarks().isEmpty()) return

        val firstFaceLandmarks = result.faceLandmarks()[0]
        val flattened = FloatArray(firstFaceLandmarks.size * 3)
        for (i in firstFaceLandmarks.indices) {
            val point = firstFaceLandmarks[i]
            flattened[i * 3] = point.x()
            flattened[i * 3 + 1] = point.y()
            flattened[i * 3 + 2] = point.z()
        }

        MainActivity.pushFaceLandmarks(flattened)
        val jsonStr = MainActivity.getAnalysisJson("")
        requireActivity().runOnUiThread { updateUI(jsonStr) }
    }

    override fun onError(error: String, errorCode: Int) {
        Log.e(TAG, "FaceLandmarker Error: $error")
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onEmpty() {}

    // --- UI Updates ---

    private fun updateUI(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val context = json.getJSONObject("context")
            val emotionData = json.getJSONObject("emotion_data")

            val isCalibrated = emotionData.optBoolean("is_calibrated", false)
            val currentEmotion = emotionData.optString("current_emotion", "Neutral")
            val energy = context.optInt("energy_level", 3)
            val stress = context.optInt("stress_level", 1)

            if (isCalibrated) {
                binding.overlayCalibration.visibility = View.GONE
            } else {
                binding.overlayCalibration.visibility = View.VISIBLE
                return
            }

            binding.txtEmotion.text = currentEmotion.uppercase()
            binding.txtStats.text = "ENERGY: $energy | STRESS: $stress"

        } catch (e: Exception) {
            Log.e(TAG, "JSON Parse Error: ${e.message}")
        }
    }

    // --- Model Management ---

    private fun checkAndDownloadModel() {
        if (needsStoragePermission() && !hasStoragePermission()) {
            requestStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            return
        }

        if (!modelDownloadHelper.isModelDownloaded()) {
            android.app.AlertDialog.Builder(requireContext())
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
                    if (modelDownloadHelper.isModelDownloaded()) {
                        llmInferenceHelper.initModel()
                        Toast.makeText(requireContext(), "Model found! Ready to analyze.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Model not found. Please follow the instructions.", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Copy ADB Command") { _, _ ->
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText(
                        "ADB Command",
                        "adb push model.task /data/data/com.example.recemotion/files/model.task"
                    )
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(requireContext(), "Command copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
                .setCancelable(false)
                .show()
        } else {
            llmInferenceHelper.initModel()
        }
    }

    private fun copyModelFromUri(uri: Uri): Boolean {
        return try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(uri, flags)

            val fileName = getFileName(uri)
            Log.i(TAG, "Selected file: $fileName")

            if (!isSupportedModelFormat(fileName)) {
                val errorMsg = "Unsupported format: $fileName. Supported: .bin, .task"
                Log.e(TAG, errorMsg)
                requireActivity().runOnUiThread {
                    binding.txtResult.text = "Error: $errorMsg\n\nFor MediaPipe LLM, please select a .bin or .task file."
                }
                Toast.makeText(requireContext(), "Unsupported format. Supported: .bin, .task", Toast.LENGTH_SHORT).show()
                return false
            }

            val extension = fileName.substringAfterLast(".")
            val targetFile = java.io.File(requireContext().filesDir, "model.$extension")
            Log.i(TAG, "Copying to: ${targetFile.absolutePath}")

            requireContext().contentResolver.openInputStream(uri).use { inputStream ->
                if (inputStream == null) return false
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
            Log.i(TAG, "Model copied successfully to: ${targetFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model file", e)
            requireActivity().runOnUiThread {
                binding.txtResult.text = "Error copying model file:\n${e.message}\n\nStack trace:\n${e.stackTraceToString()}"
            }
            false
        }
    }

    private fun getFileName(uri: Uri): String {
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst()) it.getString(index) else ModelDownloadHelper.MODEL_FILENAME
        } ?: ModelDownloadHelper.MODEL_FILENAME
    }

    private fun isSupportedModelFormat(fileName: String): Boolean {
        return fileName.substringAfterLast(".").lowercase() in listOf("bin", "task")
    }

    private fun needsStoragePermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    private fun hasStoragePermission(): Boolean {
        if (!needsStoragePermission()) return true
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // --- Coroutine Collectors ---

    private fun collectLlmResults() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                llmInferenceHelper.partialResults.collect { part ->
                    binding.txtResult.append(part)
                    binding.scrollResult.post {
                        binding.scrollResult.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }
    }

    private fun collectLlmProgress() {
        viewLifecycleOwner.lifecycleScope.launch {
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
    }

    private fun collectThoughtAnalysisState() {
        viewLifecycleOwner.lifecycleScope.launch {
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

    // --- ViewModel Factory ---

    private fun createThoughtAnalysisViewModel(): ThoughtAnalysisViewModel {
        val db = AppDatabase.getInstance(requireContext())
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

    companion object {
        const val TAG = "MainScreenFragment"
    }
}
