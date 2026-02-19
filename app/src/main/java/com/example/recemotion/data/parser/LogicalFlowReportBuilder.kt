package com.example.recemotion.data.parser

import com.example.recemotion.domain.model.AnalyzedSentence
import com.example.recemotion.domain.model.LogicalFlowAnalysis
import com.example.recemotion.domain.model.LogicalFlowReport
import com.example.recemotion.domain.model.Misalignment
import com.example.recemotion.domain.model.QuestionType
import com.example.recemotion.domain.model.RelationType
import com.example.recemotion.domain.model.UserResponse
import com.example.recemotion.domain.model.VerificationQuestion
import com.example.recemotion.domain.model.VerificationResult

/**
 * ユーザーの回答を受け取り、乖離分析レポートを構築する。
 *
 * buildPhase1Report : Phase 1 & 2 の解析結果テキスト（Q&A前に表示）
 * buildReport       : Phase 4 の乖離分析（数値化）
 * buildFinalReport  : Phase 5 の最終テキストレポート
 */
class LogicalFlowReportBuilder {

    // ─── Phase 1 & 2 テキストレポート ────────────────────────────

    fun buildPhase1Report(analysis: LogicalFlowAnalysis): String = buildString {
        append("═══════ 論理フロー解析結果 ═══════\n\n")

        // Phase 1: 文構造
        append("【Phase 1: 文構造解析】\n")
        for (sent in analysis.sentences) {
            append("\n▶ 文${sent.sentenceId + 1}: ${sent.originalText}\n")
            append("  主語  : ${sent.structure.subject.ifEmpty { "(省略)" }}\n")
            append("  述語  : ${sent.structure.verb.ifEmpty { "(不明)" }}\n")
            if (sent.structure.obj.isNotEmpty()) {
                append("  目的語: ${sent.structure.obj}\n")
            }
            if (sent.timeMarkers.isNotEmpty()) {
                append("  時間  : ${sent.timeMarkers.joinToString(", ")}\n")
            }
            if (sent.entities.isNotEmpty()) {
                val ent = sent.entities.joinToString(" / ") { "${it.value}(${it.type.label})" }
                append("  実体  : $ent\n")
            }
        }

        // Phase 2: 文間関係
        append("\n───────────────────────\n")
        append("【Phase 2: 文間の論理関係】\n")
        if (analysis.relations.isEmpty()) {
            append("  （文が1つのみ）\n")
        } else {
            for (rel in analysis.relations) {
                val confLabel = when {
                    rel.confidence >= 85 -> "高"
                    rel.confidence >= 60 -> "中"
                    else -> "低(暗黙)"
                }
                val connDisp = if (rel.connector == "implicit") "暗黙" else "「${rel.connector}」"
                append(
                    "\n  文${rel.fromSentence + 1} ──→ 文${rel.toSentence + 1}\n" +
                        "  種別: ${rel.relationType.label}  接続: $connDisp  信頼度: ${rel.confidence}%($confLabel)\n"
                )
            }
        }

        // 全体フロー
        append("\n───────────────────────\n")
        append("【全体フロー（システム解釈）】\n")
        analysis.overallFlow.forEachIndexed { i, flow ->
            append("  ${i + 1}. $flow\n")
            if (i < analysis.relations.size) {
                val rel = analysis.relations[i]
                append("      ↓ (${rel.relationType.label})\n")
            }
        }

        append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
        append("解析完了。次のフェーズで内容を検証します。")
    }

    // ─── Phase 4: 乖離分析 ──────────────────────────────────────

    fun buildReport(
        analysis: LogicalFlowAnalysis,
        questions: List<VerificationQuestion>,
        userResponses: List<UserResponse>
    ): LogicalFlowReport {
        val responseMap = userResponses.associateBy { it.questionId }
        val verificationResults = mutableListOf<VerificationResult>()
        val misalignments = mutableListOf<Misalignment>()
        var totalScore = 100

        for (sentence in analysis.sentences) {
            var alignScore = 100
            var discrepancy: String? = null

            val relatedQs = questions.filter { sentence.sentenceId in it.relatedSentences }

            for (q in relatedQs) {
                val response = responseMap[q.id] ?: continue
                val selected = response.selectedOption

                when (q.type) {
                    QuestionType.FLOW_ORDER -> when {
                        "大幅に" in selected -> {
                            alignScore -= 40; totalScore -= 25
                            discrepancy = "全体の流れが意図と大幅に異なる"
                            misalignments.add(
                                Misalignment(
                                    location = "全体フロー",
                                    issue = "システムが抽出した順序がユーザーの意図と大幅に異なる",
                                    suggestion = "「その後」「次に」などの時系列マーカーを明示的に使用してください",
                                    severity = "critical"
                                )
                            )
                        }
                        "一部" in selected -> {
                            alignScore -= 20; totalScore -= 10
                            discrepancy = "一部の順序または内容に修正が必要"
                            misalignments.add(
                                Misalignment(
                                    location = "全体フロー",
                                    issue = "一部の文の順序・解釈が意図と異なる",
                                    suggestion = "文間に接続詞を補完すると解析精度が上がります",
                                    severity = "warning"
                                )
                            )
                        }
                    }

                    QuestionType.CAUSAL_LINK -> when {
                        "単なる時系列" in selected -> {
                            alignScore -= 30; totalScore -= 15
                            discrepancy = "因果として解釈されたが実際は時系列順"
                            misalignments.add(
                                Misalignment(
                                    location = "文${sentence.sentenceId + 1}",
                                    issue = "因果関係として抽出されたが、ユーザーの意図は時系列",
                                    suggestion = "「その後」「次に」などの時系列マーカーを使用してください",
                                    severity = "warning"
                                )
                            )
                        }
                        "間接的" in selected -> {
                            alignScore -= 15; totalScore -= 8
                            discrepancy = "因果の強度が意図より強く解釈された"
                            misalignments.add(
                                Misalignment(
                                    location = "文${sentence.sentenceId + 1}",
                                    issue = "直接因果として解釈されたが、実際は間接的な影響",
                                    suggestion = "「間接的に」「部分的に」などの修飾語を加えることを検討してください",
                                    severity = "info"
                                )
                            )
                        }
                    }

                    QuestionType.SUBJECT_CHANGE -> {
                        if ("いいえ" in selected || "同じ主体" in selected) {
                            alignScore -= 25; totalScore -= 12
                            discrepancy = "主語省略により誤った主体変化として解釈"
                            misalignments.add(
                                Misalignment(
                                    location = "文${sentence.sentenceId + 1}",
                                    issue = "主語が省略されたため主体の変化として誤解釈した",
                                    suggestion = "主語を明示的に書くことで誤解を防げます",
                                    severity = "warning"
                                )
                            )
                        }
                    }

                    QuestionType.IMPLICIT_LINK -> {
                        // ユーザーが選択した関係種別とシステム解釈を比較
                        val relatedRel = analysis.relations.find {
                            it.fromSentence == sentence.sentenceId ||
                                it.toSentence == sentence.sentenceId
                        }
                        val userRelType = when {
                            "時系列" in selected -> RelationType.TEMPORAL
                            "因果" in selected -> RelationType.CAUSAL
                            "対比" in selected -> RelationType.CONTRAST
                            "継続" in selected -> RelationType.CONTINUATION
                            else -> null
                        }
                        if (relatedRel != null && userRelType != null &&
                            relatedRel.relationType != userRelType
                        ) {
                            alignScore -= 20; totalScore -= 10
                            discrepancy = "文間の関係タイプが意図と異なる" +
                                "（${relatedRel.relationType.label} → ${userRelType.label}）"
                            misalignments.add(
                                Misalignment(
                                    location = "文${sentence.sentenceId + 1}〜${sentence.sentenceId + 2}",
                                    issue = "暗黙の関係が${relatedRel.relationType.label}と解釈されたが、意図は${userRelType.label}",
                                    suggestion = "接続詞を明示することで意図が正確に伝わります",
                                    severity = "warning"
                                )
                            )
                        }
                    }
                }
            }

            val alignmentLabel = when {
                alignScore >= 90 -> "✅ 一致"
                alignScore >= 70 -> "⚠️ 部分乖離"
                else -> "❌ 乖離"
            }

            verificationResults.add(
                VerificationResult(
                    sentenceId = sentence.sentenceId,
                    extractedFlow = buildExtractedFlow(sentence),
                    alignmentScore = alignScore.coerceIn(0, 100),
                    alignmentLabel = alignmentLabel,
                    discrepancy = discrepancy
                )
            )
        }

        return LogicalFlowReport(
            analysis = analysis,
            userResponses = userResponses,
            verificationResults = verificationResults,
            overallAlignmentScore = totalScore.coerceIn(0, 100),
            criticalMisalignments = misalignments
        )
    }

    // ─── Phase 5: 最終テキストレポート ──────────────────────────

    fun buildFinalReport(report: LogicalFlowReport): String = buildString {
        append("═══════ 論理フロー検証レポート ═══════\n\n")

        // 全体フロー（検証後）
        append("■ 全体の流れ（検証後）:\n")
        report.analysis.overallFlow.forEachIndexed { i, flow ->
            append("  ${i + 1}. $flow\n")
            if (i < report.analysis.relations.size) {
                val rel = report.analysis.relations[i]
                append("      ↓ (${rel.relationType.label})\n")
            }
        }

        // 文ごとの検証状況
        append("\n■ 検証状況:\n")
        for (result in report.verificationResults) {
            append("  文${result.sentenceId + 1}: ${result.alignmentLabel} (${result.alignmentScore}%)")
            if (result.discrepancy != null) {
                append("\n    └ ${result.discrepancy}")
            }
            append("\n")
        }

        // 検出された乖離
        append("\n■ 検出された乖離:\n")
        if (report.criticalMisalignments.isEmpty()) {
            append("  なし — 論理フローは意図と一致しています ✅\n")
        } else {
            for (m in report.criticalMisalignments) {
                val icon = when (m.severity) {
                    "critical" -> "[重大]"
                    "warning" -> "[警告]"
                    else -> "[情報]"
                }
                append("\n  $icon ${m.location}\n")
                append("    問題: ${m.issue}\n")
                append("    提案: ${m.suggestion}\n")
            }
        }

        // 総合スコア
        append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
        val scoreLabel = when {
            report.overallAlignmentScore >= 90 -> "優秀"
            report.overallAlignmentScore >= 75 -> "良好"
            report.overallAlignmentScore >= 60 -> "要改善"
            else -> "大幅な修正が必要"
        }
        append("■ 総合スコア: ${report.overallAlignmentScore}% ($scoreLabel)\n")

        if (report.criticalMisalignments.isNotEmpty()) {
            append("\n■ 推奨事項:\n")
            report.criticalMisalignments.distinctBy { it.suggestion }.forEach { m ->
                append("  • ${m.suggestion}\n")
            }
        }
    }

    // ─── ヘルパー ──────────────────────────────────────────────

    private fun buildExtractedFlow(sentence: AnalyzedSentence): String {
        val subj = sentence.structure.subject.ifEmpty { "?" }
        val objPart = if (sentence.structure.obj.isNotEmpty()) "「${sentence.structure.obj}」を" else ""
        val verb = sentence.structure.verb.ifEmpty { "..." }
        val time = if (sentence.timeMarkers.isNotEmpty()) "[${sentence.timeMarkers.joinToString(",")}] " else ""
        return "$time$subj が $objPart$verb"
    }
}
