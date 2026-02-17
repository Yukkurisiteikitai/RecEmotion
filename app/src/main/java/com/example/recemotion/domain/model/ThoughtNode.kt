package com.example.recemotion.domain.model

/**
 * A single node in a thought structure tree.
 */
data class ThoughtNode(
    val id: String,
    val text: String,
    val children: List<ThoughtNode> = emptyList()
)
