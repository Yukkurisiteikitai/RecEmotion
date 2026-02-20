package com.example.recemotion.data.parser

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * 本物の CaboCha (NDK / JNI) を使った係り受け解析器。
 *
 * 使用前に [DictionaryManager.install] で辞書を filesDir にコピーしておく必要がある。
 * 辞書がない場合は error="init_failed" の JSON が返り、1チャンクのフォールバック結果になる。
 */
class NativeCabochaParser(
    private val mecabDicDir: String,
    private val cabochaModelDir: String = ""
) : DependencyParser {

    companion object {
        private const val TAG = "NativeCabochaParser"

        init {
            try {
                System.loadLibrary("cabocha_jni")
                Log.i(TAG, "cabocha_jni loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load cabocha_jni: ${e.message}")
            }
        }
    }

    /** CaboCha でパースし JSON を返すネイティブ関数。 */
    private external fun nativeParse(
        mecabDicDir: String,
        cabochaModel: String,
        text: String
    ): String

    /** 辞書・モデルロードが成功するか検証する。0=OK, 1=init失敗, 2=parse失敗 */
    external fun nativeVerify(mecabDicDir: String, cabochaModelDir: String): Int

    override suspend fun parse(text: String): CabochaResult = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext CabochaResult()

        Log.d(TAG, "nativeParse: length=${text.length}, dic=$mecabDicDir")

        val json = try {
            nativeParse(mecabDicDir, cabochaModelDir, text)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "JNI not available: ${e.message}")
            return@withContext CabochaResult()
        }

        parseJson(json)
    }

    private fun parseJson(json: String): CabochaResult {
        return try {
            val root = JSONObject(json)
            val chunksArr = root.getJSONArray("chunks")
            val chunks = (0 until chunksArr.length()).map { i ->
                val c = chunksArr.getJSONObject(i)
                val tokensArr = c.getJSONArray("tokens")
                val tokens = (0 until tokensArr.length()).map { j ->
                    val t = tokensArr.getJSONObject(j)
                    CabochaToken(
                        surface = t.optString("surface"),
                        pos = t.optString("pos")
                    )
                }
                CabochaChunk(
                    id = c.getInt("id"),
                    link = c.getInt("link"),
                    tokens = tokens
                )
            }
            CabochaResult(chunks = chunks)
        } catch (e: JSONException) {
            Log.e(TAG, "JSON parse error: ${e.message}\njson=$json")
            CabochaResult()
        }
    }
}
