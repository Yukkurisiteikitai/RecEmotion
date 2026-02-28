package com.example.recemotion.domain.service

import com.example.recemotion.domain.model.ThoughtStructure

/**
 * Domain interface for parsing raw text into a ThoughtStructure tree.
 * Abstracts the dependency parser + mapper pipeline from the use case.
 */
interface IThoughtStructureParser {
    suspend fun parse(text: String): ThoughtStructure
}
