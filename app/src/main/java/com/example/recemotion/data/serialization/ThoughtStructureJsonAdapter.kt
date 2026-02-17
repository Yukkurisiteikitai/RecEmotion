package com.example.recemotion.data.serialization

import com.example.recemotion.domain.model.ThoughtNode
import com.example.recemotion.domain.model.ThoughtStructure
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes ThoughtStructure to JSON for storage.
 */
class ThoughtStructureJsonAdapter {

    fun toJson(structure: ThoughtStructure): String {
        val root = JSONObject()
        root.put("roots", nodesToJson(structure.roots))
        return root.toString()
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
}
