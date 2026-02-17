package com.example.recemotion.data.parser

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * CaboCha dependency parser using JNI when available.
 */
class CabochaDependencyParser : DependencyParser {

    override suspend fun parse(text: String): CabochaResult = withContext(Dispatchers.Default) {
        if (text.isBlank()) {
            Log.d(TAG, "Cabocha parse skipped: empty input")
            return@withContext CabochaResult()
        }

        Log.d(TAG, "Cabocha parse start: length=${text.length}")
        val nativeJson = runCatching { nativeParse(text) }.getOrNull()
        if (!nativeJson.isNullOrBlank()) {
            Log.d(TAG, "Cabocha native result length=${nativeJson.length}")
            return@withContext parseJson(nativeJson)
        }

        Log.w(TAG, "Cabocha native parse returned empty result, using fallback")

        // Fallback: treat the whole text as a single chunk.
        val token = CabochaToken(surface = text.trim())
        val chunk = CabochaChunk(id = 0, link = -1, tokens = listOf(token))
        CabochaResult(chunks = listOf(chunk))
    }

    private fun parseJson(payload: String): CabochaResult {
        val root = JSONObject(payload)
        val chunksJson = root.optJSONArray("chunks") ?: JSONArray()
        val chunks = mutableListOf<CabochaChunk>()

        Log.d(TAG, "Cabocha chunks=${chunksJson.length()}")

        for (i in 0 until chunksJson.length()) {
            val chunkObj = chunksJson.getJSONObject(i)
            val id = chunkObj.optInt("id", i)
            val link = chunkObj.optInt("link", -1)
            val tokensJson = chunkObj.optJSONArray("tokens") ?: JSONArray()
            val tokens = mutableListOf<CabochaToken>()

            for (t in 0 until tokensJson.length()) {
                val tokenObj = tokensJson.getJSONObject(t)
                val surface = tokenObj.optString("surface", "")
                val pos = tokenObj.optString("pos", "")
                if (surface.isNotEmpty()) {
                    tokens.add(CabochaToken(surface = surface, pos = pos))
                }
            }

            chunks.add(CabochaChunk(id = id, link = link, tokens = tokens))
        }

        Log.d(TAG, "Cabocha parsed chunks=${chunks.size}")
        for (chunk in chunks) {
            val tokenText = chunk.tokens.joinToString("|") { token ->
                if (token.pos.isBlank()) token.surface else "${token.surface}/${token.pos}"
            }
            Log.d(
                TAG,
                "Chunk id=${chunk.id} link=${chunk.link} text='${chunk.text}' tokens=[$tokenText]"
            )
        }

        Log.d(TAG, "Cabocha dependencies (chunk -> target)")
        for (chunk in chunks) {
            val target = chunks.firstOrNull { it.id == chunk.link }
            val targetText = target?.text ?: "ROOT"
            val sourceTokens = chunk.tokens.joinToString("|") { it.surface }
            val targetTokens = target?.tokens?.joinToString("|") { it.surface } ?: ""
            Log.d(
                TAG,
                "${chunk.text} -> ${targetText} | src=[$sourceTokens] dst=[$targetTokens]"
            )
        }

        return CabochaResult(chunks = chunks)
    }

    private external fun nativeParse(text: String): String?

    companion object {
        private const val TAG = "CabochaParser"
    }
}
