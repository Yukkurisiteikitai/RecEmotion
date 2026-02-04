package com.example.recemotion

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloadHelper(private val context: Context) {

    companion object {
        const val TAG = "ModelDownloadHelper"
        
        // Gemma 2B IT Q4 (Quantized for mobile) - ~1.5GB
        // Alternative: Use a smaller test model or host your own
        const val MODEL_URL = "https://huggingface.co/google/gemma-2b-it-gpu-int4/resolve/main/gemma-2b-it-gpu-int4.bin"
        const val MODEL_FILENAME = "model.bin"
    }

    private val modelFile = File(context.filesDir, MODEL_FILENAME)

    fun isModelDownloaded(): Boolean {
        return modelFile.exists() && modelFile.length() > 0
    }

    suspend fun downloadModel(onProgress: (Int) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting model download from: $MODEL_URL")
            
            val url = URL(MODEL_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}"))
            }

            val fileLength = connection.contentLength
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(modelFile)

            val buffer = ByteArray(8192)
            var totalBytesRead = 0L
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
                if (fileLength > 0) {
                    val progress = ((totalBytesRead * 100) / fileLength).toInt()
                    withContext(Dispatchers.Main) {
                        onProgress(progress)
                    }
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            Log.i(TAG, "Model downloaded successfully: ${modelFile.absolutePath}")
            Result.success(modelFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            if (modelFile.exists()) {
                modelFile.delete()
            }
            Result.failure(e)
        }
    }
}
