package com.example.recemotion.data.parser

import com.example.recemotion.domain.model.ThoughtStructure
import com.example.recemotion.domain.service.IThoughtStructureParser
import javax.inject.Inject

/**
 * Adapts the DependencyParser + CabochaThoughtMapper pipeline to the domain [IThoughtStructureParser] interface.
 */
class ThoughtStructureParserAdapter @Inject constructor(
    private val parser: DependencyParser,
    private val mapper: CabochaThoughtMapper
) : IThoughtStructureParser {

    override suspend fun parse(text: String): ThoughtStructure {
        val parsed = parser.parse(text)
        return mapper.map(parsed)
    }
}
