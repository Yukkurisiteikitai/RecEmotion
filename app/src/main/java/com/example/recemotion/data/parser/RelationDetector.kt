package com.example.recemotion.data.parser

import com.example.recemotion.domain.model.AnalyzedSentence
import com.example.recemotion.domain.model.LogicalRelation
import com.example.recemotion.domain.model.RelationType
import javax.inject.Inject

/**
 * Detects logical relations (temporal, causal, contrast, etc.) between adjacent sentences
 * using a connector-word dictionary.
 */
class RelationDetector @Inject constructor() {

    companion object {
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

    fun detect(sentences: List<AnalyzedSentence>, rawSentences: List<String>): List<LogicalRelation> {
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

    private fun detectConnector(text: String): Triple<RelationType, String, Int> {
        val trimmed = text.trim()

        // Prefix match (high confidence)
        for ((connectors, relType) in ALL_CONNECTOR_MAPS) {
            for ((conn, conf) in connectors) {
                if (trimmed.startsWith(conn)) return Triple(relType, conn, conf)
            }
        }
        // Internal match (80% confidence)
        for ((connectors, relType) in ALL_CONNECTOR_MAPS) {
            for ((conn, conf) in connectors) {
                if (conn in trimmed) return Triple(relType, conn, (conf * 0.8).toInt())
            }
        }

        return Triple(RelationType.CONTINUATION, "implicit", 40)
    }
}
