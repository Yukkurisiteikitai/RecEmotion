package com.example.recemotion.data.parser

import com.example.recemotion.domain.model.ThoughtNode
import com.example.recemotion.domain.model.ThoughtStructure

/**
 * Maps CaboCha output into a tree structure.
 */
class CabochaThoughtMapper {

    fun map(result: CabochaResult): ThoughtStructure {
        if (result.chunks.isEmpty()) {
            return ThoughtStructure()
        }

        val nodes = result.chunks.associate { chunk ->
            chunk.id to ThoughtNode(id = chunk.id.toString(), text = chunk.text)
        }.toMutableMap()

        val childrenMap = mutableMapOf<Int, MutableList<ThoughtNode>>()
        for (chunk in result.chunks) {
            if (chunk.link >= 0) {
                val child = nodes[chunk.id] ?: continue
                val list = childrenMap.getOrPut(chunk.link) { mutableListOf() }
                list.add(child)
            }
        }

        val roots = mutableListOf<ThoughtNode>()
        for (chunk in result.chunks) {
            val node = nodes[chunk.id] ?: continue
            val children = childrenMap[node.id.toInt()].orEmpty()
            val updated = node.copy(children = children)
            nodes[chunk.id] = updated
            if (chunk.link < 0) {
                roots.add(updated)
            }
        }

        if (roots.isEmpty()) {
            roots.addAll(nodes.values)
        }

        return ThoughtStructure(roots = roots)
    }
}
