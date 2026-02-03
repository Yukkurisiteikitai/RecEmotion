
package com.example.recemotion

import android.Manifest
import android.app.TimePickerDialog
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
import com.example.recemotion.databinding.ActivityMainBinding
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        faceLandmarkerHelper = FaceLandmarkerHelper(context = this, faceLandmarkerHelperListener = this)

        setupUI()
        
        // Default Wake Time: 7:00 AM Today
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 7)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        wakeTimeUnix = cal.timeInMillis / 1000
        
        // Init Session
        initSession(wakeTimeUnix)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
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
        
        // Reset Button
        binding.btnReset.setOnClickListener {
            initSession(wakeTimeUnix)
            // No toast needed, overlay will appear
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
            
            // Get JSON
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
