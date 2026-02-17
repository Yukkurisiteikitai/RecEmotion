package com.example.recemotion.data.parser

/**
 * Lightweight representation of CaboCha output.
 */
data class CabochaResult(
    val chunks: List<CabochaChunk> = emptyList()
)

data class CabochaChunk(
    val id: Int,
    val link: Int,
    val tokens: List<CabochaToken> = emptyList()
) {
    val text: String
        get() = tokens.joinToString("") { it.surface }
}

data class CabochaToken(
    val surface: String,
    val pos: String = ""
)
