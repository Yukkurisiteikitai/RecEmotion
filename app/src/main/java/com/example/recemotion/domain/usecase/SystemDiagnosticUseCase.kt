package com.example.recemotion.domain.usecase

import com.example.recemotion.domain.model.DiagnosticMessage
import com.example.recemotion.domain.service.IResourceChecker
import javax.inject.Inject

class SystemDiagnosticUseCase @Inject constructor(
    private val resourceChecker: IResourceChecker
) {
    fun runDiagnostic(): List<DiagnosticMessage> {
        val logs = mutableListOf<DiagnosticMessage>()

        // 1. MeCab Dictionary
        if (resourceChecker.isDictionaryInstalled()) {
            logs.add(DiagnosticMessage("✅ MeCab Dictionary: Installed"))
        } else {
            logs.add(DiagnosticMessage("❌ MeCab Dictionary: Missing", isError = true))
        }

        // 2. CaboCha Models
        if (resourceChecker.isCabochaModelInstalled()) {
            logs.add(DiagnosticMessage("✅ CaboCha Models: Installed"))
        } else {
            logs.add(DiagnosticMessage("❌ CaboCha Models: Missing", isError = true))
        }

        // 3. MediaPipe LLM Model
        val modelFile = resourceChecker.resolveLlmModelFile()
        if (modelFile != null) {
            val sizeMB = modelFile.length() / (1024 * 1024)
            logs.add(DiagnosticMessage("✅ MediaPipe LLM: Found (${sizeMB}MB)"))
        } else {
            logs.add(DiagnosticMessage("⚠️ MediaPipe LLM: Not found in internal storage or Downloads", isError = true))
        }

        return logs
    }
}
