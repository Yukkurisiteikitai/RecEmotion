package com.example.recemotion.data.parser

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

/**
 * Kuromoji ベースの [CabochaDependencyParser] と
 * NDK ネイティブの [NativeCabochaParser] を同一テキストで実行して
 * 結果を比較するユーティリティ。
 *
 * 比較項目:
 *  - チャンク数
 *  - 各チャンクの表層文字列とリンク先
 *  - 係り受けリンクの一致率
 *  - 実行時間
 */
object ParserComparisonLogger {

    private const val TAG = "ParserComparison"

    data class ComparisonResult(
        val text: String,
        val kuromojiChunks: List<CabochaChunk>,
        val nativeChunks: List<CabochaChunk>,
        val kuromojiTimeMs: Long,
        val nativeTimeMs: Long,
        val linkMatchRate: Float,   // 0.0f〜1.0f
        val chunkCountDiff: Int,    // native - kuromoji
        val summary: String
    )

    /**
     * 2つのパーサーで [text] を解析し比較結果を返す。
     * nativeParser が null (辞書未インストール) の場合は Kuromoji 結果のみ記録する。
     */
    suspend fun compare(
        text: String,
        kuromojiParser: CabochaDependencyParser,
        nativeParser: NativeCabochaParser?
    ): ComparisonResult = withContext(Dispatchers.Default) {

        // --- Kuromoji パース ---
        var kuromojiResult = CabochaResult()
        val kuromojiMs = measureTimeMillis {
            kuromojiResult = kuromojiParser.parse(text)
        }

        // --- Native CaboCha パース ---
        var nativeResult = CabochaResult()
        val nativeMs = measureTimeMillis {
            if (nativeParser != null) {
                nativeResult = nativeParser.parse(text)
            }
        }

        // --- 比較 ---
        val kChunks = kuromojiResult.chunks
        val nChunks = nativeResult.chunks
        val chunkCountDiff = nChunks.size - kChunks.size

        // リンク一致率: min(size) 個のチャンクを順に比較
        val compareCount = minOf(kChunks.size, nChunks.size)
        val matchCount = if (compareCount == 0) 0 else
            (0 until compareCount).count { i ->
                kChunks[i].link == nChunks[i].link
            }
        val linkMatchRate = if (compareCount == 0) 0f else matchCount.toFloat() / compareCount

        val summary = buildSummary(
            text, kChunks, nChunks,
            kuromojiMs, nativeMs,
            linkMatchRate, chunkCountDiff,
            nativeParser == null
        )

        Log.i(TAG, summary)

        ComparisonResult(
            text = text,
            kuromojiChunks = kChunks,
            nativeChunks = nChunks,
            kuromojiTimeMs = kuromojiMs,
            nativeTimeMs = nativeMs,
            linkMatchRate = linkMatchRate,
            chunkCountDiff = chunkCountDiff,
            summary = summary
        )
    }

    private fun buildSummary(
        text: String,
        kChunks: List<CabochaChunk>,
        nChunks: List<CabochaChunk>,
        kMs: Long,
        nMs: Long,
        matchRate: Float,
        countDiff: Int,
        nativeUnavailable: Boolean
    ): String = buildString {
        appendLine("========== Parser Comparison ==========")
        appendLine("Text: \"$text\"")
        appendLine()

        // --- Kuromoji ---
        appendLine("[Kuromoji (近似)] ${kMs}ms  チャンク数=${kChunks.size}")
        kChunks.forEach { c ->
            val link = if (c.link == -1) "ROOT" else "→ chunk[${c.link}]"
            appendLine("  chunk[${c.id}] \"${c.text}\" $link")
        }

        appendLine()

        // --- Native CaboCha ---
        if (nativeUnavailable) {
            appendLine("[Native CaboCha] 辞書未インストールのためスキップ")
        } else {
            appendLine("[Native CaboCha (本物)] ${nMs}ms  チャンク数=${nChunks.size}")
            nChunks.forEach { c ->
                val link = if (c.link == -1) "ROOT" else "→ chunk[${c.link}]"
                appendLine("  chunk[${c.id}] \"${c.text}\" $link")
            }
        }

        appendLine()
        appendLine("--- 差分 ---")
        appendLine("チャンク数の差: ${if (countDiff >= 0) "+$countDiff" else "$countDiff"}")
        if (!nativeUnavailable) {
            appendLine("リンク一致率: ${"%.1f".format(matchRate * 100)}%")
            appendLine("実行時間差:  Kuromoji ${kMs}ms / Native ${nMs}ms")

            // チャンク表層の差分
            val maxIdx = maxOf(kChunks.size, nChunks.size)
            val diffs = (0 until maxIdx).mapNotNull { i ->
                val kText = kChunks.getOrNull(i)?.text ?: "(なし)"
                val nText = nChunks.getOrNull(i)?.text ?: "(なし)"
                if (kText != nText) "  [${i}] Kuromoji=\"$kText\" / Native=\"$nText\"" else null
            }
            if (diffs.isEmpty()) {
                appendLine("チャンク表層: 完全一致")
            } else {
                appendLine("チャンク表層の差分:")
                diffs.forEach { appendLine(it) }
            }
        }
        appendLine("=======================================")
    }

    /**
     * 複数のテストテキストでベンチマークを実行し Logcat に出力する。
     */
    suspend fun runBenchmark(
        kuromojiParser: CabochaDependencyParser,
        nativeParser: NativeCabochaParser?
    ) {
        val testSentences = listOf(
            "今日は良い天気でした。",
            "私はリンゴとバナナを食べた。",
            "彼女が書いた手紙は美しかった。",
            "日本語の係り受け解析はとても難しい問題だ。",
            "機械学習を使って感情を認識するアプリを開発しています。"
        )

        Log.i(TAG, "=== Benchmark Start (${testSentences.size} sentences) ===")
        var totalKMs = 0L
        var totalNMs = 0L
        var totalMatchRate = 0f

        testSentences.forEachIndexed { idx, sentence ->
            val result = compare(sentence, kuromojiParser, nativeParser)
            totalKMs += result.kuromojiTimeMs
            totalNMs += result.nativeTimeMs
            totalMatchRate += result.linkMatchRate
            Log.d(TAG, "[$idx] linkMatch=${"%.0f".format(result.linkMatchRate * 100)}%")
        }

        val n = testSentences.size
        Log.i(TAG, "=== Benchmark Summary ===")
        Log.i(TAG, "Avg Kuromoji: ${totalKMs / n}ms")
        Log.i(TAG, "Avg Native:   ${totalNMs / n}ms")
        Log.i(TAG, "Avg LinkMatch: ${"%.1f".format(totalMatchRate / n * 100)}%")
        Log.i(TAG, "========================")
    }
}
