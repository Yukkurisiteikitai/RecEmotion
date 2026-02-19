package com.example.recemotion.data.parser

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MeCab (IPAdic) 辞書を assets から内部ストレージへコピーする管理クラス。
 *
 * MeCab は mmap() でファイルシステム上の辞書を読み込むため、
 * assets から直接アクセスすることができない。
 * 初回起動時に assets/ipadic/ 以下のファイルを filesDir/ipadic/ へコピーする。
 *
 * assets ディレクトリ構造:
 *   assets/ipadic/char.bin
 *   assets/ipadic/dicrc
 *   assets/ipadic/left-id.def
 *   assets/ipadic/matrix.bin
 *   assets/ipadic/pos-id.def
 *   assets/ipadic/rewrite.def
 *   assets/ipadic/right-id.def
 *   assets/ipadic/sys.dic
 *   assets/ipadic/unk.dic
 */
class DictionaryManager(private val context: Context) {

    companion object {
        private const val TAG = "DictionaryManager"
        private const val ASSETS_DIR = "ipadic"

        private val DICT_FILES = listOf(
            "char.bin",
            "dicrc",
            "left-id.def",
            "matrix.bin",
            "pos-id.def",
            "rewrite.def",
            "right-id.def",
            "sys.dic",
            "unk.dic"
        )
    }

    /** filesDir 以下の辞書ディレクトリパス（NativeCabochaParser に渡す） */
    val dictPath: String
        get() = File(context.filesDir, ASSETS_DIR).absolutePath

    /** 辞書がすべてインストール済みか確認する */
    fun isInstalled(): Boolean {
        val dir = File(dictPath)
        if (!dir.exists()) return false
        return DICT_FILES.all { File(dir, it).exists() }
    }

    /**
     * assets から内部ストレージへ辞書ファイルをコピーする。
     * 既にコピー済みのファイルはスキップする。
     */
    suspend fun install() = withContext(Dispatchers.IO) {
        val dir = File(dictPath).apply { mkdirs() }
        val assets = context.assets

        DICT_FILES.forEach { name ->
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

        Log.i(TAG, "Dictionary install complete: $dictPath")
    }

    /** インストール済みファイルをすべて削除してリセットする */
    fun reset() {
        File(dictPath).deleteRecursively()
        Log.i(TAG, "Dictionary reset")
    }

    /** インストール済み辞書のサイズ合計（バイト）を返す */
    fun installedSize(): Long {
        val dir = File(dictPath)
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
