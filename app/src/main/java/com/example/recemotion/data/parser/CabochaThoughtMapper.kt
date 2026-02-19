package com.example.recemotion.data.parser

import android.util.Log
import com.example.recemotion.domain.model.ThoughtNode
import com.example.recemotion.domain.model.ThoughtStructure

/**
 * Maps CaboCha output into a tree structure.
 */
class CabochaThoughtMapper {

    fun map(result: CabochaResult): ThoughtStructure {
        if (result.chunks.isEmpty()) {
            Log.d(TAG, "Thought map skipped: no chunks")
            return ThoughtStructure()
        }

        Log.d(TAG, "Thought map start: chunks=${result.chunks.size}")

        val chunksById = result.chunks.associateBy { it.id }

        // 問題8 修正:
        // 旧実装は ThoughtNode への直接参照を childrenMap に格納していた。
        // その後 nodes[id] = updated で更新しても childrenMap の参照は古いまま（children 空）だった。
        // → チャンクIDのみを childrenMap に記録し、buildNode() で再帰的にノードを構築する。
        val childrenIds = mutableMapOf<Int, MutableList<Int>>()
        for (chunk in result.chunks) {
            if (chunk.link >= 0) {
                childrenIds.getOrPut(chunk.link) { mutableListOf() }.add(chunk.id)
            }
        }

        // 再帰的にノードを構築することで、children が常に正しく設定される
        fun buildNode(chunkId: Int): ThoughtNode {
            val chunk = chunksById[chunkId]
                ?: return ThoughtNode(id = chunkId.toString(), text = "")
            return ThoughtNode(
                id = chunkId.toString(),
                text = chunk.text,
                children = (childrenIds[chunkId] ?: emptyList()).map { buildNode(it) }
            )
        }

        val roots = result.chunks
            .filter { it.link < 0 }
            .map { buildNode(it.id) }

        val finalRoots = roots.ifEmpty { result.chunks.map { buildNode(it.id) } }

        Log.d(TAG, "Thought map done: roots=${finalRoots.size}")
        return ThoughtStructure(roots = finalRoots)
    }

    companion object {
        private const val TAG = "CabochaThoughtMapper"
    }
}
