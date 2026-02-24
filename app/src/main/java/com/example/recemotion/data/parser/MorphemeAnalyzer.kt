package com.example.recemotion.data.parser

import android.util.Log
import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer
import com.example.recemotion.domain.model.AnalyzedSentence
import com.example.recemotion.domain.model.EntityInfo
import com.example.recemotion.domain.model.EntityType
import com.example.recemotion.domain.model.MorphemeInfo
import com.example.recemotion.domain.model.SentenceStructure
import javax.inject.Inject

/**
 * Morphological analysis for a single sentence.
 *
 * Internally switches between NativeCabochaParser (chunk-based) and
 * Kuromoji (morpheme-based) depending on whether [nativeParser] is available.
 * This resolves the OCP violation of the original null-check switch.
 */
class MorphemeAnalyzer @Inject constructor() {

    private val tokenizer by lazy { Tokenizer() }

    companion object {
        private const val TAG = "MorphemeAnalyzer"

        private val TIME_MARKERS = setOf(
            "去年", "昨年", "今年", "来年", "先月", "今月", "来月",
            "昨日", "今日", "明日", "今朝", "今夜", "今週", "先週",
            "その後", "その時", "その際", "次に", "まず", "最初に",
            "それから", "しばらく", "ずっと", "当時", "以来", "以前",
            "最近", "かつて", "かねて", "最終的に", "最終的"
        )

        private val PERSON_PRONOUNS = setOf(
            "私", "僕", "俺", "彼", "彼女", "あなた", "君", "彼ら",
            "私たち", "自分", "われ", "わたくし", "うち"
        )
    }

    /**
     * Analyzes a single sentence. Uses [nativeParser] when available, otherwise Kuromoji.
     */
    suspend fun analyze(sentenceId: Int, text: String, nativeParser: NativeCabochaParser?): AnalyzedSentence {
        return if (nativeParser != null) {
            analyzeWithNative(sentenceId, text, nativeParser)
        } else {
            analyzeWithKuromoji(sentenceId, text)
        }
    }

    // ── NativeCabochaParser (chunk-based) ─────────────────────────────────

    private suspend fun analyzeWithNative(
        id: Int,
        text: String,
        parser: NativeCabochaParser
    ): AnalyzedSentence {
        return try {
            val result = parser.parse(text)
            val morphemes = result.chunks.flatMap { chunk ->
                chunk.tokens.map { token ->
                    MorphemeInfo(surface = token.surface, pos = token.pos, pos2 = "", baseForm = token.surface)
                }
            }
            AnalyzedSentence(
                sentenceId = id,
                originalText = text,
                morphemes = morphemes,
                structure = SentenceStructure(
                    subject = extractSubjectFromChunks(result.chunks),
                    verb = extractVerbFromChunks(result.chunks),
                    obj = extractObjectFromChunks(result.chunks)
                ),
                timeMarkers = detectTimeMarkersFromChunks(result.chunks),
                entities = extractEntitiesFromChunks(result.chunks)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Native analysis failed for sentence $id: ${e.message}")
            emptyAnalyzedSentence(id, text)
        }
    }

    private fun extractSubjectFromChunks(chunks: List<CabochaChunk>): String {
        for (chunk in chunks) {
            for (i in 1 until chunk.tokens.size) {
                val tok = chunk.tokens[i]
                if (tok.pos == "助詞" && tok.surface in setOf("は", "が")) {
                    return chunk.tokens.take(i).joinToString("") { it.surface }
                }
            }
        }
        return ""
    }

    private fun extractVerbFromChunks(chunks: List<CabochaChunk>): String {
        val root = chunks.firstOrNull { it.link == -1 } ?: return ""
        return root.tokens.lastOrNull { it.pos == "動詞" }?.surface
            ?: root.tokens.lastOrNull()?.surface
            ?: ""
    }

    private fun extractObjectFromChunks(chunks: List<CabochaChunk>): String {
        for (chunk in chunks) {
            for (i in 1 until chunk.tokens.size) {
                val tok = chunk.tokens[i]
                if (tok.surface == "を" && tok.pos == "助詞") {
                    return chunk.tokens.take(i).joinToString("") { it.surface }
                }
            }
        }
        return ""
    }

    private fun detectTimeMarkersFromChunks(chunks: List<CabochaChunk>): List<String> =
        chunks.flatMap { chunk ->
            chunk.tokens.filter { it.surface in TIME_MARKERS }.map { it.surface }
        }.distinct()

    private fun extractEntitiesFromChunks(chunks: List<CabochaChunk>): List<EntityInfo> {
        val entities = mutableListOf<EntityInfo>()
        val seen = mutableSetOf<String>()
        for (chunk in chunks) {
            for (token in chunk.tokens) {
                val surface = token.surface
                if (surface in seen) continue
                when {
                    surface in PERSON_PRONOUNS -> {
                        entities.add(EntityInfo(EntityType.PERSON, surface)); seen.add(surface)
                    }
                    token.pos == "動詞" -> {
                        entities.add(EntityInfo(EntityType.ACTION, surface)); seen.add(surface)
                    }
                }
            }
        }
        return entities
    }

    // ── Kuromoji (morpheme-based) ─────────────────────────────────────────

    private fun analyzeWithKuromoji(id: Int, text: String): AnalyzedSentence {
        return try {
            val tokens = tokenizer.tokenize(text)
            val morphemes = tokens.map { tok ->
                MorphemeInfo(
                    surface = tok.surface,
                    pos = tok.partOfSpeechLevel1,
                    pos2 = tok.partOfSpeechLevel2,
                    baseForm = tok.baseForm
                )
            }
            AnalyzedSentence(
                sentenceId = id,
                originalText = text,
                morphemes = morphemes,
                structure = SentenceStructure(
                    subject = extractSubject(tokens),
                    verb = extractVerb(tokens),
                    obj = extractObject(tokens)
                ),
                timeMarkers = detectTimeMarkers(tokens),
                entities = extractEntities(tokens)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Kuromoji analysis failed for sentence $id: ${e.message}")
            emptyAnalyzedSentence(id, text)
        }
    }

    private fun extractSubject(tokens: List<Token>): String {
        for (i in 1 until tokens.size) {
            val tok = tokens[i]
            if (tok.partOfSpeechLevel1 == "助詞" && tok.surface in setOf("は", "が")) {
                val sb = StringBuilder()
                var j = i - 1
                while (j >= 0) {
                    val prev = tokens[j]
                    if (prev.partOfSpeechLevel1 == "名詞" || prev.surface in PERSON_PRONOUNS) {
                        sb.insert(0, prev.surface); j--
                    } else break
                }
                val subject = sb.toString()
                if (subject.isNotEmpty()) return subject
            }
        }
        return ""
    }

    private fun extractVerb(tokens: List<Token>): String {
        for (tok in tokens.reversed()) {
            if (tok.partOfSpeechLevel1 == "動詞" && tok.partOfSpeechLevel2 != "非自立") {
                return tok.baseForm.takeIf { it.isNotBlank() } ?: tok.surface
            }
        }
        for (tok in tokens.reversed()) {
            if (tok.partOfSpeechLevel1 in listOf("形容詞", "形容動詞")) {
                return tok.baseForm.takeIf { it.isNotBlank() } ?: tok.surface
            }
        }
        return ""
    }

    private fun extractObject(tokens: List<Token>): String {
        for (i in 1 until tokens.size) {
            if (tokens[i].surface == "を" && tokens[i].partOfSpeechLevel1 == "助詞") {
                val sb = StringBuilder()
                var j = i - 1
                while (j >= 0 && tokens[j].partOfSpeechLevel1 == "名詞") {
                    sb.insert(0, tokens[j].surface); j--
                }
                val obj = sb.toString()
                if (obj.isNotEmpty()) return obj
            }
        }
        return ""
    }

    private fun detectTimeMarkers(tokens: List<Token>): List<String> {
        val markers = mutableListOf<String>()
        for (tok in tokens) {
            if (tok.surface in TIME_MARKERS) { markers.add(tok.surface); continue }
            if (tok.partOfSpeechLevel1 == "名詞" &&
                tok.partOfSpeechLevel2 in listOf("時相名詞", "副詞可能")
            ) {
                if (tok.surface !in markers) markers.add(tok.surface)
            }
        }
        return markers.distinct()
    }

    private fun extractEntities(tokens: List<Token>): List<EntityInfo> {
        val entities = mutableListOf<EntityInfo>()
        val seen = mutableSetOf<String>()
        for (tok in tokens) {
            val surface = tok.surface
            if (surface in seen) continue
            when {
                surface in PERSON_PRONOUNS -> {
                    entities.add(EntityInfo(EntityType.PERSON, surface)); seen.add(surface)
                }
                tok.partOfSpeechLevel1 == "名詞" &&
                        tok.partOfSpeechLevel2 in listOf("固有名詞", "組織") -> {
                    entities.add(EntityInfo(EntityType.ORGANIZATION, surface)); seen.add(surface)
                }
                tok.partOfSpeechLevel1 == "動詞" &&
                        tok.partOfSpeechLevel2 in listOf("自立", "") -> {
                    val base = tok.baseForm.takeIf { it.isNotBlank() } ?: surface
                    if (base !in seen) { entities.add(EntityInfo(EntityType.ACTION, base)); seen.add(base) }
                }
            }
        }
        return entities
    }

    private fun emptyAnalyzedSentence(id: Int, text: String) = AnalyzedSentence(
        sentenceId = id, originalText = text,
        morphemes = emptyList(), structure = SentenceStructure("", "", ""),
        timeMarkers = emptyList(), entities = emptyList()
    )
}
