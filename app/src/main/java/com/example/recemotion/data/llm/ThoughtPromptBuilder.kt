package com.example.recemotion.data.llm

import com.example.recemotion.domain.model.ThoughtNode
import com.example.recemotion.domain.model.ThoughtStructure
import javax.inject.Inject

/**
 * Builds a strict JSON-only prompt for thought analysis.
 */
class ThoughtPromptBuilder @Inject constructor() {

    /**
     * 通常の解析プロンプトを構築する。
     *
     * @param structure 解析対象の思考ツリー
     * @param emotionContext 感情タイムラインのログ文字列（省略可）。
     *   渡された場合はプロンプトに「Emotion State Log」セクションとして追記する。
     */
    fun build(structure: ThoughtStructure, emotionContext: String? = null): String {
        val treeText = buildIndentedText(structure)
        val emotionSection = if (!emotionContext.isNullOrBlank()) {
            "\nEmotion State Log (facial emotion captured during input):\n$emotionContext\n"
        } else ""
        return """
You are an analysis engine. Analyze the following ThoughtTree and categorize the findings.
Return ONLY JSON. Do not include extra text.

ThoughtTree:
$treeText
$emotionSection
Output JSON schema:
{
  "premises": ["fundamental starting points"],
  "emotions": ["detected emotional states"],
  "statedFacts": ["explicitly mentioned facts in the text"],
  "assumptions": [
    {
      "text": "underlying belief or prediction not explicitly stated",
      "importance": 1-5,
      "verificationGoal": "what to ask or do to verify this assumption"
    }
  ],
  "possibleBiases": [],
  "missingPerspectives": []
}
""".trimIndent()
    }

    private fun buildIndentedText(structure: ThoughtStructure): String {
        if (structure.roots.isEmpty()) return "(empty)"
        val builder = StringBuilder()
        for (root in structure.roots) {
            appendNode(builder, root, 0)
        }
        return builder.toString().trimEnd()
    }

    private fun appendNode(builder: StringBuilder, node: ThoughtNode, depth: Int) {
        val indent = "  ".repeat(depth)
        builder.append(indent).append("- ").append(node.text).append('\n')
        for (child in node.children) {
            appendNode(builder, child, depth + 1)
        }
    }
}
