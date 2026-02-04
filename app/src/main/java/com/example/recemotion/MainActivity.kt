
package com.example.recemotion

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
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
import androidx.lifecycle.lifecycleScope
import com.example.recemotion.databinding.ActivityMainBinding
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.flow.collect
import org.json.JSONObject
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
        lifecycleScope.launchWhenStarted {
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

    private fun checkAndDownloadModel() {
        if (!modelDownloadHelper.isModelDownloaded()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("LLM Model Required")
                .setMessage("""
                    This app requires a Gemma 2B model (~2.6GB) for on-device analysis.
                    
                    Download Steps:
                    1. Visit: https://huggingface.co/litert-community/Gemma2-2B-IT
                    2. Accept the Gemma license (login required)
                    3. Download 'gemma2-2b-it-int8-web.task.bin' (2.63 GB)
                    4. Rename to 'model.bin'
                    5. Push to device:
                       adb push model.bin /data/data/com.example.recemotion/files/model.bin
                    
                    Alternative: Use 'Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.task' (2.71 GB)
                """.trimIndent())
                .setPositiveButton("I've Downloaded It") { _, _ ->
                    // Recheck if model exists
                    if (modelDownloadHelper.isModelDownloaded()) {
                        llmInferenceHelper.initModel()
                        Toast.makeText(this, "Model found! Ready to analyze.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Model not found. Please follow the instructions.", Toast.LENGTH_LONG).show()
                    }
                }
                .setNeutralButton("Copy ADB Command") { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("ADB Command", 
                        "adb push model.bin /data/data/com.example.recemotion/files/model.bin")
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "Command copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Skip for Now") { _, _ ->
                    Toast.makeText(this, "Analysis feature will be disabled.", Toast.LENGTH_SHORT).show()
                }
                .setCancelable(false)
                .show()
        } else {
            // Model exists, initialize LLM
            llmInferenceHelper.initModel()
        }
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

        // Analyze Button
        binding.btnAnalyze.setOnClickListener {
            val text = binding.edtReflection.text.toString()
            if (text.isEmpty()) {
                Toast.makeText(this, "Please write a reflection first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show Result View
            binding.scrollResult.visibility = View.VISIBLE
            binding.txtResult.text = "Analyzing...\n"
            
            // Generate Prompt JSON
            val jsonContext = getAnalysisJson(text)
            
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

            llmInferenceHelper.generateResponse(prompt)

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
            System.loadLibrary("recemotion")
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
