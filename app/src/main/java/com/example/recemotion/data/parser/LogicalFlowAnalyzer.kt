package com.example.recemotion.data.parser

import android.util.Log
import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer
import com.example.recemotion.domain.model.AnalyzedSentence
import com.example.recemotion.domain.model.EntityInfo
import com.example.recemotion.domain.model.EntityType
import com.example.recemotion.domain.model.LogicalFlowAnalysis
import com.example.recemotion.domain.model.LogicalRelation
import com.example.recemotion.domain.model.MorphemeInfo
import com.example.recemotion.domain.model.RelationType
import com.example.recemotion.domain.model.SentenceStructure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 論理フロー解析エンジン。
 *
 * [nativeParser] が null の場合は Kuromoji（形態素ベース）で解析する。
 * [nativeParser] が渡された場合は NativeCabochaParser（チャンクベース）で解析する。
 *
 * Phase 1: 各文の解析 → 主語・述語・目的語・時間マーカー・エンティティを抽出
 * Phase 2: 文間の論理関係（時系列/因果/対比/継続/具体例）を接続詞から検出
 */
class LogicalFlowAnalyzer(
    private val nativeParser: NativeCabochaParser? = null
) {

    private val tokenizer by lazy { Tokenizer() }

    companion object {
        private const val TAG = "LogicalFlowAnalyzer"

        private val SENTENCE_ENDS = setOf('。', '！', '？', '.', '!', '?')

        /** 時間マーカーのホワイトリスト */
        private val TIME_MARKERS = setOf(
            "去年", "昨年", "今年", "来年", "先月", "今月", "来月",
            "昨日", "今日", "明日", "今朝", "今夜", "今週", "先週",
            "その後", "その時", "その際", "次に", "まず", "最初に",
            "それから", "しばらく", "ずっと", "当時", "以来", "以前",
            "最近", "かつて", "かねて", "最終的に", "最終的"
        )

        /** 人称代名詞リスト */
        private val PERSON_PRONOUNS = setOf(
            "私", "僕", "俺", "彼", "彼女", "あなた", "君", "彼ら",
            "私たち", "自分", "われ", "わたくし", "うち"
        )

        // ── 文間接続詞マップ（接続詞 → 信頼度） ──────────────────────

        private val TEMPORAL_CONNECTORS = mapOf(
            "その後" to 95, "それから" to 95, "次に" to 90,
            "まず" to 88, "最初に" to 88, "その次" to 88,
            "しばらくして" to 85, "やがて" to 82, "続いて" to 82,
            "一方で" to 75, "並行して" to 75
        )

        private val CAUSAL_CONNECTORS = mapOf(
            "なぜなら" to 95, "だから" to 92, "それで" to 90,
            "そのため" to 90, "その結果" to 92, "ゆえに" to 90,
            "したがって" to 90, "よって" to 88, "このため" to 88,
            "それゆえ" to 85, "ために" to 72
        )

        private val CONTRAST_CONNECTORS = mapOf(
            "しかし" to 95, "でも" to 90, "一方" to 88,
            "ところが" to 90, "けれども" to 88, "ただし" to 85,
            "それでも" to 82, "むしろ" to 80, "とはいえ" to 80,
            "他方" to 85, "ところで" to 68
        )

        private val CONTINUATION_CONNECTORS = mapOf(
            "また" to 85, "そして" to 85, "さらに" to 85,
            "加えて" to 85, "しかも" to 80, "それに" to 80,
            "なお" to 75
        )

        private val EXEMPLIFICATION_CONNECTORS = mapOf(
            "例えば" to 95, "たとえば" to 95, "具体的には" to 92,
            "実際に" to 80, "特に" to 72, "とりわけ" to 72
        )

        private val ALL_CONNECTOR_MAPS = listOf(
            TEMPORAL_CONNECTORS to RelationType.TEMPORAL,
            CAUSAL_CONNECTORS to RelationType.CAUSAL,
            CONTRAST_CONNECTORS to RelationType.CONTRAST,
            CONTINUATION_CONNECTORS to RelationType.CONTINUATION,
            EXEMPLIFICATION_CONNECTORS to RelationType.EXEMPLIFICATION
        )
    }

    // ─── 公開メソッド ──────────────────────────────────────────────

    suspend fun analyze(text: String): LogicalFlowAnalysis = withContext(Dispatchers.Default) {
        val rawSentences = splitSentences(text)
        Log.d(TAG, "Split into ${rawSentences.size} sentences (parser=${if (nativeParser != null) "CaboCha" else "Kuromoji"})")

        val analyzed = if (nativeParser != null) {
            analyzeWithNative(rawSentences)
        } else {
            rawSentences.mapIndexed { idx, s -> analyzeSentence(idx, s) }
        }
        val relations = detectRelations(analyzed, rawSentences)
        val overallFlow = buildOverallFlow(analyzed)

        LogicalFlowAnalysis(analyzed, relations, overallFlow)
    }

    // ─── NativeCabochaParser（チャンクベース）解析 ──────────────────────

    private suspend fun analyzeWithNative(sentences: List<String>): List<AnalyzedSentence> {
        return sentences.mapIndexed { idx, s ->
            try {
                val result = nativeParser!!.parse(s)
                val morphemes = result.chunks.flatMap { chunk ->
                    chunk.tokens.map { token ->
                        MorphemeInfo(
                            surface = token.surface,
                            pos    = token.pos,
                            pos2   = "",           // NativeCabochaParser は pos2 を持たない
                            baseForm = token.surface
                        )
                    }
                }
                AnalyzedSentence(
                    sentenceId  = idx,
                    originalText = s,
                    morphemes   = morphemes,
                    structure   = SentenceStructure(
                        subject = extractSubjectFromChunks(result.chunks),
                        verb    = extractVerbFromChunks(result.chunks),
                        obj     = extractObjectFromChunks(result.chunks)
                    ),
                    timeMarkers = detectTimeMarkersFromChunks(result.chunks),
                    entities    = extractEntitiesFromChunks(result.chunks)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to analyze sentence $idx with native parser: ${e.message}")
                AnalyzedSentence(
                    sentenceId = idx, originalText = s,
                    morphemes = emptyList(),
                    structure = SentenceStructure("", "", ""),
                    timeMarkers = emptyList(), entities = emptyList()
                )
            }
        }
    }

    /** チャンク内で は/が の直前にあるトークン列を主語として返す */
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

    /** ROOT チャンク（link=-1）の末尾動詞トークンを述語として返す */
    private fun extractVerbFromChunks(chunks: List<CabochaChunk>): String {
        val root = chunks.firstOrNull { it.link == -1 } ?: return ""
        return root.tokens.lastOrNull { it.pos == "動詞" }?.surface
            ?: root.tokens.lastOrNull()?.surface
            ?: ""
    }

    /** チャンク内で を の直前にあるトークン列を目的語として返す */
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
                        entities.add(EntityInfo(EntityType.PERSON, surface))
                        seen.add(surface)
                    }
                    token.pos == "動詞" -> {
                        entities.add(EntityInfo(EntityType.ACTION, surface))
                        seen.add(surface)
                    }
                }
            }
        }
        return entities
    }

    // ─── 文分割 ────────────────────────────────────────────────────

    private fun splitSentences(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (char in text) {
            current.append(char)
            if (char in SENTENCE_ENDS) {
                val s = current.toString().trim()
                if (s.isNotEmpty()) result.add(s)
                current.clear()
            }
        }
        val remaining = current.toString().trim()
        if (remaining.isNotEmpty()) result.add(remaining)
        return result
    }

    // ─── 1文の解析 ─────────────────────────────────────────────────

    private fun analyzeSentence(id: Int, text: String): AnalyzedSentence {
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
            Log.e(TAG, "Failed to analyze sentence $id: ${e.message}")
            AnalyzedSentence(
                sentenceId = id,
                originalText = text,
                morphemes = emptyList(),
                structure = SentenceStructure("", "", ""),
                timeMarkers = emptyList(),
                entities = emptyList()
            )
        }
    }

    // ─── 主語抽出: は / が の直前の名詞連続を取得 ──────────────────

    private fun extractSubject(tokens: List<Token>): String {
        for (i in 1 until tokens.size) {
            val tok = tokens[i]
            if (tok.partOfSpeechLevel1 == "助詞" && tok.surface in setOf("は", "が")) {
                val sb = StringBuilder()
                var j = i - 1
                while (j >= 0) {
                    val prev = tokens[j]
                    if (prev.partOfSpeechLevel1 == "名詞" || prev.surface in PERSON_PRONOUNS) {
                        sb.insert(0, prev.surface)
                        j--
                    } else break
                }
                val subject = sb.toString()
                if (subject.isNotEmpty()) return subject
            }
        }
        return ""
    }

    // ─── 述語抽出: 文末から最初の自立動詞 ──────────────────────────

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

    // ─── 目的語抽出: を の直前の名詞連続 ───────────────────────────

    private fun extractObject(tokens: List<Token>): String {
        for (i in 1 until tokens.size) {
            if (tokens[i].surface == "を" && tokens[i].partOfSpeechLevel1 == "助詞") {
                val sb = StringBuilder()
                var j = i - 1
                while (j >= 0 && tokens[j].partOfSpeechLevel1 == "名詞") {
                    sb.insert(0, tokens[j].surface)
                    j--
                }
                val obj = sb.toString()
                if (obj.isNotEmpty()) return obj
            }
        }
        return ""
    }

    // ─── 時間マーカー検出 ─────────────────────────────────────────

    private fun detectTimeMarkers(tokens: List<Token>): List<String> {
        val markers = mutableListOf<String>()
        for (tok in tokens) {
            if (tok.surface in TIME_MARKERS) {
                markers.add(tok.surface)
                continue
            }
            // Kuromoji ipadic では名詞-副詞可能 に時相名詞が含まれることがある
            if (tok.partOfSpeechLevel1 == "名詞" &&
                tok.partOfSpeechLevel2 in listOf("時相名詞", "副詞可能")
            ) {
                if (tok.surface !in markers) markers.add(tok.surface)
            }
        }
        return markers.distinct()
    }

    // ─── エンティティ抽出 ─────────────────────────────────────────

    private fun extractEntities(tokens: List<Token>): List<EntityInfo> {
        val entities = mutableListOf<EntityInfo>()
        val seen = mutableSetOf<String>()

        for (tok in tokens) {
            val surface = tok.surface
            if (surface in seen) continue

            when {
                surface in PERSON_PRONOUNS -> {
                    entities.add(EntityInfo(EntityType.PERSON, surface))
                    seen.add(surface)
                }
                tok.partOfSpeechLevel1 == "名詞" &&
                        tok.partOfSpeechLevel2 in listOf("固有名詞", "組織") -> {
                    entities.add(EntityInfo(EntityType.ORGANIZATION, surface))
                    seen.add(surface)
                }
                tok.partOfSpeechLevel1 == "動詞" &&
                        tok.partOfSpeechLevel2 in listOf("自立", "") -> {
                    val base = tok.baseForm.takeIf { it.isNotBlank() } ?: surface
                    if (base !in seen) {
                        entities.add(EntityInfo(EntityType.ACTION, base))
                        seen.add(base)
                    }
                }
            }
        }
        return entities
    }

    // ─── 文間の関係検出 ──────────────────────────────────────────

    private fun detectRelations(
        sentences: List<AnalyzedSentence>,
        rawSentences: List<String>
    ): List<LogicalRelation> {
        return (0 until sentences.size - 1).map { i ->
            val nextText = rawSentences.getOrNull(i + 1) ?: ""
            val (relType, connector, confidence) = detectConnector(nextText)
            LogicalRelation(
                fromSentence = i,
                toSentence = i + 1,
                relationType = relType,
                connector = connector,
                confidence = confidence
            )
        }
    }

    /**
     * テキストの先頭・内部から接続詞を検出し、関係種別・接続詞・信頼度を返す。
     * 先頭一致優先、なければ内部一致（信頼度 80%降格）。
     */
    private fun detectConnector(text: String): Triple<RelationType, String, Int> {
        val trimmed = text.trim()

        // 先頭一致（高信頼）
        for ((connectors, relType) in ALL_CONNECTOR_MAPS) {
            for ((conn, conf) in connectors) {
                if (trimmed.startsWith(conn)) return Triple(relType, conn, conf)
            }
        }
        // 内部一致（信頼度を 80% に下げる）
        for ((connectors, relType) in ALL_CONNECTOR_MAPS) {
            for ((conn, conf) in connectors) {
                if (conn in trimmed) return Triple(relType, conn, (conf * 0.8).toInt())
            }
        }

        return Triple(RelationType.CONTINUATION, "implicit", 40)
    }

    // ─── 全体フローの概要文生成 ─────────────────────────────────

    private fun buildOverallFlow(sentences: List<AnalyzedSentence>): List<String> {
        return sentences.map { s ->
            val timePrefix = if (s.timeMarkers.isNotEmpty()) "[${s.timeMarkers.first()}] " else ""
            val subj = s.structure.subject.ifEmpty { "(主語不明)" }
            val objPart = if (s.structure.obj.isNotEmpty()) "「${s.structure.obj}」を" else ""
            val verb = s.structure.verb.ifEmpty { "(述語不明)" }
            "$timePrefix$subj が ${objPart}${verb}"
        }
    }
}
