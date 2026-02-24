package com.example.recemotion.data.parser

import javax.inject.Inject

/**
 * Splits raw Japanese text into individual sentences using punctuation boundaries.
 */
class SentenceTokenizer @Inject constructor() {

    private val sentenceEnds = setOf('。', '！', '？', '.', '!', '?')

    fun split(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (char in text) {
            current.append(char)
            if (char in sentenceEnds) {
                val s = current.toString().trim()
                if (s.isNotEmpty()) result.add(s)
                current.clear()
            }
        }
        val remaining = current.toString().trim()
        if (remaining.isNotEmpty()) result.add(remaining)
        return result
    }
}
