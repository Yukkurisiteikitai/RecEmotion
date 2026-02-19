package com.example.recemotion.data.parser

import com.example.recemotion.domain.model.LogicalFlowAnalysis
import com.example.recemotion.domain.model.QuestionType
import com.example.recemotion.domain.model.RelationType
import com.example.recemotion.domain.model.VerificationQuestion

/**
 * 論理フロー解析結果から検証質問を自動生成する。
 *
 * 生成される質問の種類:
 *  - FLOW_ORDER    : 全体の順序・内容確認（複数文がある場合）
 *  - IMPLICIT_LINK : 信頼度が低い（< 60%）暗黙の文間関係
 *  - CAUSAL_LINK   : 因果関係と判定された文間の強度確認
 *  - SUBJECT_CHANGE: 主体（主語）の変化が検出された文間
 */
class LogicalFlowQuestionGenerator {

    fun generateQuestions(analysis: LogicalFlowAnalysis): List<VerificationQuestion> {
        val questions = mutableListOf<VerificationQuestion>()
        var qId = 0

        // ── Q1: 全体フローの確認（2文以上のとき） ────────────────

        if (analysis.sentences.size > 1) {
            val flowText = analysis.overallFlow
                .mapIndexed { i, f -> "  ${i + 1}. $f" }
                .joinToString("\n")
            questions.add(
                VerificationQuestion(
                    id = qId++,
                    type = QuestionType.FLOW_ORDER,
                    questionText = "システムが理解した全体の流れです:\n\n$flowText\n\nこの順序・内容は正しいですか？",
                    relatedSentences = analysis.sentences.map { it.sentenceId },
                    options = listOf("正しい", "一部異なる", "大幅に異なる")
                )
            )
        }

        // ── 文間関係ごとの質問 ────────────────────────────────────

        for (rel in analysis.relations) {
            val fromSent = analysis.sentences.getOrNull(rel.fromSentence) ?: continue
            val toSent = analysis.sentences.getOrNull(rel.toSentence) ?: continue

            val fromPreview = fromSent.originalText.preview(28)
            val toPreview = toSent.originalText.preview(28)

            // 暗黙の関係（信頼度低）→ 関係種別を確認
            if (rel.confidence < 60) {
                questions.add(
                    VerificationQuestion(
                        id = qId++,
                        type = QuestionType.IMPLICIT_LINK,
                        questionText = "文${rel.fromSentence + 1}:「$fromPreview」\n→ 文${rel.toSentence + 1}:「$toPreview」\n\nこの2文のつながりはどのような関係ですか？",
                        relatedSentences = listOf(rel.fromSentence, rel.toSentence),
                        options = listOf(
                            "時系列（その後・次に）",
                            "因果（だから・その結果）",
                            "対比（しかし・一方）",
                            "継続（また・そして）"
                        )
                    )
                )
            }

            // 因果関係 → 強度を確認
            if (rel.relationType == RelationType.CAUSAL) {
                questions.add(
                    VerificationQuestion(
                        id = qId++,
                        type = QuestionType.CAUSAL_LINK,
                        questionText = "「$fromPreview」から\n「$toPreview」\nへの関係について:\n\nこれは直接の原因→結果の関係ですか？",
                        relatedSentences = listOf(rel.fromSentence, rel.toSentence),
                        options = listOf(
                            "はい、直接の因果",
                            "間接的な影響",
                            "単なる時系列順"
                        )
                    )
                )
            }

            // 主体変化 → 意図確認
            val fromSubj = fromSent.structure.subject
            val toSubj = toSent.structure.subject
            if (fromSubj.isNotEmpty() && toSubj.isNotEmpty() && fromSubj != toSubj) {
                questions.add(
                    VerificationQuestion(
                        id = qId++,
                        type = QuestionType.SUBJECT_CHANGE,
                        questionText = "話題の主体が\n「$fromSubj」→「$toSubj」\nに変化しています。\n\nこれは意図的な視点の切り替えですか？",
                        relatedSentences = listOf(rel.fromSentence, rel.toSentence),
                        options = listOf(
                            "はい、意図的な切り替え",
                            "いいえ、同じ主体の話",
                            "どちらでもない"
                        )
                    )
                )
            }
        }

        return questions
    }

    /** 長い文を省略してプレビュー文字列を生成 */
    private fun String.preview(maxLen: Int): String =
        if (length <= maxLen) this else take(maxLen) + "..."
}
