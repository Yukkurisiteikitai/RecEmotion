package com.example.recemotion

import android.content.Context
import android.os.Environment
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

        // GGUF model for llama.cpp. Host your own or provide a direct download.
        const val MODEL_URL = ""
        const val MODEL_FILENAME = "model.gguf"
    }

    private val supportedExtensions = listOf("gguf", "tflite", "bin")
    private val internalModelFile = File(context.filesDir, MODEL_FILENAME)
    private val downloadsModelFile = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        MODEL_FILENAME
    )

    fun isModelDownloaded(): Boolean {
        // 内部ストレージをチェック
        for (ext in supportedExtensions) {
            val file = File(context.filesDir, "model.$ext")
            if (file.exists() && file.length() > 0) return true
        }
        
        // Downloads をチェック
        for (ext in supportedExtensions) {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "model.$ext"
            )
            if (file.exists() && file.length() > 0) return true
        }
        
        // 古い固定名もチェック
        return (downloadsModelFile.exists() && downloadsModelFile.length() > 0) ||
            (internalModelFile.exists() && internalModelFile.length() > 0)
    }

    fun getModelFile(): File? {
        // 内部ストレージを優先
        for (ext in supportedExtensions) {
            val file = File(context.filesDir, "model.$ext")
            if (file.exists() && file.length() > 0) return file
        }
        
        // Downloads を次に
        for (ext in supportedExtensions) {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "model.$ext"
            )
            if (file.exists() && file.length() > 0) return file
        }
        
        // 古い固定名
        if (downloadsModelFile.exists() && downloadsModelFile.length() > 0) {
            return downloadsModelFile
        }
        if (internalModelFile.exists() && internalModelFile.length() > 0) {
            return internalModelFile
        }
        return null
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
            val outputStream = FileOutputStream(internalModelFile)

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

            Log.i(TAG, "Model downloaded successfully: ${internalModelFile.absolutePath}")
            Result.success(internalModelFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            if (internalModelFile.exists()) {
                internalModelFile.delete()
            }
            Result.failure(e)
        }
    }
}
