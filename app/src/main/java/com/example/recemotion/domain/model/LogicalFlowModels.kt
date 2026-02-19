package com.example.recemotion.domain.model

// ─── 品詞・エンティティ種別 ───────────────────────────────────────

enum class EntityType(val label: String) {
    PERSON("人物"),
    ORGANIZATION("組織/固有名詞"),
    CONCEPT("概念"),
    ACTION("行動")
}

// ─── 文間関係の種別 ───────────────────────────────────────────────

enum class RelationType(val label: String) {
    TEMPORAL("時系列"),
    CAUSAL("因果"),
    CONTRAST("対比"),
    CONTINUATION("継続"),
    EXEMPLIFICATION("具体例")
}

// ─── 検証質問の種別 ───────────────────────────────────────────────

enum class QuestionType(val label: String) {
    FLOW_ORDER("全体フロー確認"),
    SUBJECT_CHANGE("主体変化確認"),
    CAUSAL_LINK("因果関係確認"),
    IMPLICIT_LINK("文間関係確認")
}

// ─── 形態素情報 ───────────────────────────────────────────────────

data class MorphemeInfo(
    val surface: String,
    val pos: String,
    val pos2: String = "",
    val baseForm: String = ""
)

// ─── 文構造（主語・述語・目的語） ────────────────────────────────

data class SentenceStructure(
    val subject: String,
    val verb: String,
    val obj: String = ""
)

// ─── エンティティ情報 ─────────────────────────────────────────────

data class EntityInfo(
    val type: EntityType,
    val value: String
)

// ─── 解析済み文 ──────────────────────────────────────────────────

data class AnalyzedSentence(
    val sentenceId: Int,
    val originalText: String,
    val morphemes: List<MorphemeInfo>,
    val structure: SentenceStructure,
    val timeMarkers: List<String>,
    val entities: List<EntityInfo>
)

// ─── 文間の論理関係 ───────────────────────────────────────────────

data class LogicalRelation(
    val fromSentence: Int,
    val toSentence: Int,
    val relationType: RelationType,
    val connector: String,
    val confidence: Int // 0-100
)

// ─── 検証質問 ────────────────────────────────────────────────────

data class VerificationQuestion(
    val id: Int,
    val type: QuestionType,
    val questionText: String,
    val relatedSentences: List<Int>,
    val options: List<String>
)

// ─── ユーザー回答 ────────────────────────────────────────────────

data class UserResponse(
    val questionId: Int,
    val selectedOption: String,
    val questionType: QuestionType,
    val relatedSentences: List<Int>
)

// ─── 検証結果（文単位） ──────────────────────────────────────────

data class VerificationResult(
    val sentenceId: Int,
    val extractedFlow: String,
    val alignmentScore: Int, // 0-100
    val alignmentLabel: String, // ✅一致 / ⚠️部分乖離 / ❌乖離
    val discrepancy: String? = null
)

// ─── 乖離情報 ────────────────────────────────────────────────────

data class Misalignment(
    val location: String,
    val issue: String,
    val suggestion: String,
    val severity: String // "critical" | "warning" | "info"
)

// ─── 解析結果（Phase 1 & 2） ────────────────────────────────────

data class LogicalFlowAnalysis(
    val sentences: List<AnalyzedSentence>,
    val relations: List<LogicalRelation>,
    val overallFlow: List<String>
)

// ─── 最終レポート（Phase 5） ─────────────────────────────────────

data class LogicalFlowReport(
    val analysis: LogicalFlowAnalysis,
    val userResponses: List<UserResponse>,
    val verificationResults: List<VerificationResult>,
    val overallAlignmentScore: Int,
    val criticalMisalignments: List<Misalignment>
)
