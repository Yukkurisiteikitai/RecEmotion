package com.example.recemotion.data.parser

import android.content.Context
import android.os.Environment
import com.example.recemotion.domain.service.IResourceChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class ResourceCheckerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dictionaryManager: DictionaryManager,
    private val cabochaModelManager: CabochaModelManager
) : IResourceChecker {

    override fun isDictionaryInstalled(): Boolean = dictionaryManager.isInstalled()

    override fun isCabochaModelInstalled(): Boolean = cabochaModelManager.isInstalled()

    override fun resolveLlmModelFile(): File? {
        val supportedExtensions = listOf("bin", "task")
        for (ext in supportedExtensions) {
            val f = File(context.filesDir, "model.$ext")
            if (f.exists() && f.length() > 0) return f
        }
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        for (ext in supportedExtensions) {
            val f = File(downloadsDir, "model.$ext")
            if (f.exists() && f.length() > 0) return f
        }
        return null
    }
}
