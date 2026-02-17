package com.example.recemotion.data.llm

import com.example.recemotion.domain.model.ThoughtNode
import com.example.recemotion.domain.model.ThoughtStructure

/**
 * Builds a strict JSON-only prompt for thought analysis.
 */
class ThoughtPromptBuilder {

    fun build(structure: ThoughtStructure): String {
        val treeText = buildIndentedText(structure)
        return """
You are an analysis engine.
Return ONLY JSON. Do not include extra text.

ThoughtTree:
$treeText

Output JSON schema:
{
  "premises": [],
  "emotions": [],
  "inferences": [],
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
