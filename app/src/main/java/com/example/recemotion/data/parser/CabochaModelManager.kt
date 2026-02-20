package com.example.recemotion.data.parser

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * CaboCha モデルファイルを assets から内部ストレージへコピーする管理クラス。
 *
 * CaboCha は mmap() でファイルシステム上のモデルを読み込むため、
 * assets から直接アクセスすることができない。
 * 初回起動時に assets/cabocha_model/ 以下をコピーする。
 *
 * assets ディレクトリ構造:
 *   assets/cabocha_model/chunk.ipa.model  (~20MB)
 *   assets/cabocha_model/dep.ipa.model    (~41MB)
 *   assets/cabocha_model/ne.ipa.model     (~20MB)
 */
class CabochaModelManager(private val context: Context) {

    companion object {
        private const val TAG = "CabochaModelManager"
        private const val ASSETS_DIR = "cabocha_model"

        private val MODEL_FILES = listOf(
            "chunk.ipa.model",
            "dep.ipa.model",
            "ne.ipa.model"
        )
    }

    /** filesDir 以下のモデルディレクトリパス（NativeCabochaParser に渡す） */
    val modelPath: String
        get() = File(context.filesDir, ASSETS_DIR).absolutePath

    /** モデルファイルがすべてインストール済みか確認する */
    fun isInstalled(): Boolean {
        val dir = File(modelPath)
        if (!dir.exists()) return false
        return MODEL_FILES.all { File(dir, it).exists() }
    }

    /** assets から内部ストレージへモデルファイルをコピーする */
    suspend fun install() = withContext(Dispatchers.IO) {
        val dir = File(modelPath).apply { mkdirs() }
        val assets = context.assets

        MODEL_FILES.forEach { name ->
            val outFile = File(dir, name)
            if (outFile.exists()) return@forEach

            try {
                assets.open("$ASSETS_DIR/$name").use { input ->
                    outFile.outputStream().buffered().use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                }
                Log.d(TAG, "Copied: $name (${outFile.length()} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy $name: ${e.message}")
            }
        }

        Log.i(TAG, "Model install complete: $modelPath")
    }

    /** インストール済みファイルをすべて削除してリセットする */
    fun reset() {
        File(modelPath).deleteRecursively()
        Log.i(TAG, "Model reset")
    }
}
