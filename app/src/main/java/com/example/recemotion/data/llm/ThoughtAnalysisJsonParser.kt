package com.example.recemotion.data.llm

import com.example.recemotion.domain.model.BiasDetection
import com.example.recemotion.domain.model.MissingPerspective
import com.example.recemotion.domain.model.ThoughtAnalysisResult
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses the strict JSON response from the LLM.
 */
class ThoughtAnalysisJsonParser {

    fun parse(jsonText: String): ThoughtAnalysisResult {
        // 問題16 修正: LLM が "Sure! Here is:\n{...}" のように前置きテキストを出力した場合でも
        // 最初の '{' から最後の '}' を抽出することで JSONException を防ぐ
        val root = JSONObject(extractJson(jsonText))
        return ThoughtAnalysisResult(
            premises = readStringArray(root.optJSONArray("premises")),
            emotions = readStringArray(root.optJSONArray("emotions")),
            inferences = readStringArray(root.optJSONArray("inferences")),
            possibleBiases = readBiases(root.optJSONArray("possibleBiases")),
            missingPerspectives = readMissing(root.optJSONArray("missingPerspectives"))
        )
    }

    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else text
    }

    private fun readStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i, "")
            if (value.isNotBlank()) {
                result.add(value)
            }
        }
        return result
    }

    private fun readBiases(array: JSONArray?): List<BiasDetection> {
        if (array == null) return emptyList()
        val result = mutableListOf<BiasDetection>()
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            when (item) {
                is JSONObject -> {
                    val name = item.optString("name", "")
                    val evidence = item.optString("evidence", "")
                    if (name.isNotBlank() || evidence.isNotBlank()) {
                        result.add(BiasDetection(name = name, evidence = evidence))
                    }
                }
                is String -> if (item.isNotBlank()) {
                    result.add(BiasDetection(name = item, evidence = ""))
                }
            }
        }
        return result
    }

    private fun readMissing(array: JSONArray?): List<MissingPerspective> {
        if (array == null) return emptyList()
        val result = mutableListOf<MissingPerspective>()
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            when (item) {
                is JSONObject -> {
                    val description = item.optString("description", "")
                    if (description.isNotBlank()) {
                        result.add(MissingPerspective(description = description))
                    }
                }
                is String -> if (item.isNotBlank()) {
                    result.add(MissingPerspective(description = item))
                }
            }
        }
        return result
    }
}
