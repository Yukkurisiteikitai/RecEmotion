package com.example.recemotion.domain.usecase

import android.content.Context
import com.example.recemotion.LLMInferenceHelper
import com.example.recemotion.data.parser.CabochaModelManager
import com.example.recemotion.data.parser.DictionaryManager
import com.example.recemotion.presentation.ConversationDisplayItem
import java.io.File

class SystemDiagnosticUseCase(
    private val context: Context,
    private val dictionaryManager: DictionaryManager,
    private val cabochaModelManager: CabochaModelManager
) {
    fun runDiagnostic(): List<ConversationDisplayItem.SystemMessage> {
        val logs = mutableListOf<ConversationDisplayItem.SystemMessage>()
        var id = System.nanoTime()

        // 1. MeCab Dictionary
        if (dictionaryManager.isInstalled()) {
            logs.add(ConversationDisplayItem.SystemMessage(id++, "✅ MeCab Dictionary: Installed"))
        } else {
            logs.add(ConversationDisplayItem.SystemMessage(id++, "❌ MeCab Dictionary: Missing", isError = true))
        }

        // 2. CaboCha Models
        if (cabochaModelManager.isInstalled()) {
            logs.add(ConversationDisplayItem.SystemMessage(id++, "✅ CaboCha Models: Installed"))
        } else {
            logs.add(ConversationDisplayItem.SystemMessage(id++, "❌ CaboCha Models: Missing", isError = true))
        }

        // 3. MediaPipe LLM Model
        val modelFile = resolveModelFile()
        if (modelFile != null) {
            val sizeMB = modelFile.length() / (1024 * 1024)
            logs.add(ConversationDisplayItem.SystemMessage(id++, "✅ MediaPipe LLM: Found (${sizeMB}MB)"))
        } else {
            logs.add(ConversationDisplayItem.SystemMessage(id++, "⚠️ MediaPipe LLM: Not found in internal storage or Downloads", isError = true))
        }

        return logs
    }

    private fun resolveModelFile(): File? {
        val supportedExtensions = listOf("bin", "task")
        for (ext in supportedExtensions) {
            val f = File(context.filesDir, "model.$ext")
            if (f.exists() && f.length() > 0) return f
        }
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        for (ext in supportedExtensions) {
            val f = File(downloadsDir, "model.$ext")
            if (f.exists() && f.length() > 0) return f
        }
        return null
    }
}
