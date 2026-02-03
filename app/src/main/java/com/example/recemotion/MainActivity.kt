
package com.example.recemotion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.recemotion.databinding.ActivityMainBinding
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), FaceLandmarkerHelper.LandmarkerListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper
    private lateinit var cameraExecutor: ExecutorService

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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        
        binding.textView.text = "Initializing..."
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
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
                runOnUiThread { binding.textView.text = "Camera Started" }
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResults(result: FaceLandmarkerResult, inferenceTime: Long) {
        result.faceBlendshapes().ifPresent { blendshapes ->
            // blendshapes is a list of lists (one list per face)
            if (blendshapes.isNotEmpty()) {
                val firstFace = blendshapes[0] // Categories for the first face
                // Example: Find "jawOpen" or generic printing
                // Let's print the top 3 highest scores for debugging
                val sorted = firstFace.sortedByDescending { it.score() }.take(3)
                
                val logMsg = sorted.joinToString { "${it.displayNameOrIndex()}:${String.format("%.2f", it.score())}" }
                
                Log.d(TAG, "Face 0 Blendshapes Top 3: $logMsg")
                
                runOnUiThread {
                    binding.textView.text = "Inference: ${inferenceTime}ms\n$logMsg"
                }
            }
        }
    }

    private fun Category.displayNameOrIndex(): String {
        return this.categoryName() ?: this.index().toString()
    }

    override fun onError(error: String, errorCode: Int) {
        Log.e(TAG, "FaceLandmarker Error: $error")
        runOnUiThread {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        faceLandmarkerHelper.clearFaceLandmarker()
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
        external fun pushEmotionFrame(scores: FloatArray)

        @JvmStatic
        external fun getAnalysisJson(text: String): String

        @JvmStatic
        external fun updateStressLevel(level: Int)
    }
}
