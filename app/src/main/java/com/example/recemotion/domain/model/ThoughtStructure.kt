package com.example.recemotion.domain.model

/**
 * Root container for structured thoughts.
 */
data class ThoughtStructure(
    val roots: List<ThoughtNode> = emptyList()
)
