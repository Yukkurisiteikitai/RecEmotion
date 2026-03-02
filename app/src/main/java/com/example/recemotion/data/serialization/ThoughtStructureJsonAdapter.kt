package com.example.recemotion.data.serialization

import com.example.recemotion.domain.model.ThoughtNode
import com.example.recemotion.domain.model.ThoughtStructure
import com.example.recemotion.domain.service.IThoughtStructureSerializer
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * Serializes and deserializes ThoughtStructure to/from JSON for storage.
 */
class ThoughtStructureJsonAdapter @Inject constructor() : IThoughtStructureSerializer {

    override fun toJson(structure: ThoughtStructure): String {
        val root = JSONObject()
        root.put("roots", nodesToJson(structure.roots))
        return root.toString()
    }

    override fun fromJson(json: String): ThoughtStructure {
        if (json.isBlank() || json == "{}") return ThoughtStructure()
        val root = JSONObject(json)
        val roots = nodesFromJson(root.optJSONArray("roots") ?: JSONArray())
        return ThoughtStructure(roots)
    }

    private fun nodesToJson(nodes: List<ThoughtNode>): JSONArray {
        val array = JSONArray()
        for (node in nodes) {
            val obj = JSONObject()
            obj.put("id", node.id)
            obj.put("text", node.text)
            obj.put("children", nodesToJson(node.children))
            array.put(obj)
        }
        return array
    }

    private fun nodesFromJson(array: JSONArray): List<ThoughtNode> {
        val result = mutableListOf<ThoughtNode>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            result.add(
                ThoughtNode(
                    id = obj.optString("id", i.toString()),
                    text = obj.optString("text", ""),
                    children = nodesFromJson(obj.optJSONArray("children") ?: JSONArray())
                )
            )
        }
        return result
    }
}
