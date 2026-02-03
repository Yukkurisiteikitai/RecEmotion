package com.example.recemotion

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

class FaceLandmarkerHelper(
    val context: Context,
    val faceLandmarkerHelperListener: LandmarkerListener? = null
) {
    private var faceLandmarker: FaceLandmarker? = null

    init {
        setupFaceLandmarker()
    }

    private fun setupFaceLandmarker() {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")

        val baseOptions = baseOptionsBuilder.build()

        val optionsBuilder = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setOutputFaceBlendshapes(true) // Crucial for emotion output
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener(this::returnLivestreamResult)
            .setErrorListener(this::returnLivestreamError)

        try {
            faceLandmarker = FaceLandmarker.createFromOptions(context, optionsBuilder.build())
        } catch (e: IllegalStateException) {
            faceLandmarkerHelperListener?.onError("Face Landmarker failed to initialize. See error logs for details")
            Log.e(TAG, "MediaPipe failed to load the task with error: " + e.message)
        } catch (e: RuntimeException) {
            faceLandmarkerHelperListener?.onError("Face Landmarker failed to initialize. See error logs for details", 0)
            Log.e(TAG, "Face Landmarker failed to initialize. See error logs for details", e)
        }
    }

    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (faceLandmarker == null) {
            setupFaceLandmarker()
        }

        val frameTime = SystemClock.uptimeMillis()
        
        // Convert ImageProxy to MPImage
        // Note: For optimal performance, we should use ByteBuffer or MediaImage,
        // but Bitmap conversion is often easier to handle rotation correctly in basic setups.
        val bitmap = imageProxy.toBitmap()
        
        val mpImage = BitmapImageBuilder(bitmap).build()
        
        faceLandmarker?.detectAsync(mpImage, frameTime)
    }
    
    // Clean up
    fun clearFaceLandmarker() {
        faceLandmarker?.close()
        faceLandmarker = null
    }

    private fun returnLivestreamResult(
        result: FaceLandmarkerResult,
        input: MPImage
    ) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()

        if (result.faceBlendshapes().isPresent && result.faceBlendshapes().get().isNotEmpty()) {
             faceLandmarkerHelperListener?.onResults(result, inferenceTime)
        } else {
             faceLandmarkerHelperListener?.onEmpty()
        }
    }

    private fun returnLivestreamError(error: RuntimeException) {
        faceLandmarkerHelperListener?.onError(error.message ?: "An unknown error has occurred")
    }

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = 0)
        fun onResults(result: FaceLandmarkerResult, inferenceTime: Long)
        fun onEmpty() {}
    }

    companion object {
        const val TAG = "FaceLandmarkerHelper"
    }
}
