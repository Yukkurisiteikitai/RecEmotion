package com.example.recemotion.data.parser

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
            return@withContext CabochaResult()
        }

        val nativeJson = runCatching { nativeParse(text) }.getOrNull()
        if (!nativeJson.isNullOrBlank()) {
            return@withContext parseJson(nativeJson)
        }

        // Fallback: treat the whole text as a single chunk.
        val token = CabochaToken(surface = text.trim())
        val chunk = CabochaChunk(id = 0, link = -1, tokens = listOf(token))
        CabochaResult(chunks = listOf(chunk))
    }

    private fun parseJson(payload: String): CabochaResult {
        val root = JSONObject(payload)
        val chunksJson = root.optJSONArray("chunks") ?: JSONArray()
        val chunks = mutableListOf<CabochaChunk>()

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

        return CabochaResult(chunks = chunks)
    }

    private external fun nativeParse(text: String): String?
}
