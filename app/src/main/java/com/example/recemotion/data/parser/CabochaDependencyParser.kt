package com.example.recemotion.data.parser

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer

/**
 * Kuromoji を使った依存構造パーサー。
 *
 * 問題7 修正:
 * 旧実装は全形態素を「次の形態素へリンク」する単純な線形チェーンだった。
 * → 全ノードが1本の線に並ぶだけで係り受け構造を全く表現できていなかった。
 *
 * 新実装: 文節（bunsetsu）単位でグループ化し、各文ごとに
 * ハブ&スポーク構造（全文節が文末述語へリンク）を生成する。
 *
 * 日本語はSOV言語（述語が文末）なので、文末文節を根ノードとし
 * それ以外の文節がすべて根に係る構造が言語的に妥当。
 *
 * 例: 「今日は/良い天気/でした。」→
 *   でした。 (root, link=-1)
 *   ├── 今日は  (link → でした。)
 *   └── 良い天気 (link → でした。)
 */
class CabochaDependencyParser : DependencyParser {

    private val tokenizer by lazy { Tokenizer() }

    override suspend fun parse(text: String): CabochaResult = withContext(Dispatchers.Default) {
        if (text.isBlank()) {
            Log.d(TAG, "Cabocha parse skipped: empty input")
            return@withContext CabochaResult()
        }

        Log.d(TAG, "Parsing with Kuromoji: length=${text.length}")

        try {
            val tokens = tokenizer.tokenize(text)
            val chunks = buildChunks(tokens)
            Log.d(TAG, "Parsed ${chunks.size} chunks")
            CabochaResult(chunks = chunks)
        } catch (e: Exception) {
            Log.e(TAG, "Kuromoji parsing failed", e)
            // フォールバック: 全体を1チャンク（単一根ノード）として扱う
            val chunk = CabochaChunk(
                id = 0,
                link = -1,
                tokens = listOf(CabochaToken(surface = text.trim()))
            )
            CabochaResult(chunks = listOf(chunk))
        }
    }

    /**
     * 形態素列を文節（bunsetsu）単位にグループ化し、
     * 文ごとにハブ&スポーク構造のリンクを付与する。
     *
     * 文節境界: 助詞・助動詞・記号の直後
     * 文境界: 句読点（。！？.!?）を含む文節の直後
     */
    private fun buildChunks(tokens: List<Token>): List<CabochaChunk> {
        if (tokens.isEmpty()) return emptyList()

        // --- Step 1: トークンを文節単位にグループ化 ---
        val bunsetsuGroups = mutableListOf<List<Token>>()
        var current = mutableListOf<Token>()

        for (token in tokens) {
            current.add(token)
            val pos = token.partOfSpeechLevel1
            // 助詞・助動詞・記号の後で文節を切る
            if (pos in CHUNK_BOUNDARY_POS || token.surface in SENTENCE_END_SURFACES) {
                bunsetsuGroups.add(current.toList())
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) {
            bunsetsuGroups.add(current.toList())
        }

        // --- Step 2: 文ごとにチャンクIDとリンクを付与 ---
        // 各文の末尾文節が根ノード (link = -1)
        // それ以外の文節はすべて末尾文節にリンク
        val chunks = mutableListOf<CabochaChunk>()
        var chunkId = 0
        val sentenceBuf = mutableListOf<List<Token>>()

        fun flushSentence() {
            if (sentenceBuf.isEmpty()) return
            val n = sentenceBuf.size
            val startId = chunkId
            val lastId = chunkId + n - 1

            sentenceBuf.forEachIndexed { j, group ->
                val isLast = j == n - 1
                chunks.add(
                    CabochaChunk(
                        id = startId + j,
                        // 末尾文節は根(link=-1)、他は末尾文節へリンク
                        link = if (isLast) -1 else lastId,
                        tokens = group.map { tok ->
                            CabochaToken(surface = tok.surface, pos = tok.partOfSpeechLevel1)
                        }
                    )
                )
            }
            chunkId += n
            sentenceBuf.clear()
        }

        for (group in bunsetsuGroups) {
            sentenceBuf.add(group)
            val isSentenceEnd = group.any { it.surface in SENTENCE_END_SURFACES }
            if (isSentenceEnd) {
                flushSentence()
            }
        }
        flushSentence() // 末尾に句読点がない文も処理

        return chunks
    }

    companion object {
        private const val TAG = "CabochaDependencyParser"

        /** 文節境界となる品詞（この品詞のトークンの後で文節を切る） */
        private val CHUNK_BOUNDARY_POS = setOf("助詞", "助動詞", "記号")

        /** 文境界となる句読点 */
        private val SENTENCE_END_SURFACES = setOf("。", "！", "？", ".", "!", "?")
    }
}
