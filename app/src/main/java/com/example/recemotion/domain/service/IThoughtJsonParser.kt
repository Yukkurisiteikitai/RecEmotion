package com.example.recemotion.domain.service

import com.example.recemotion.domain.model.ThoughtAnalysisResult

/**
 * Domain interface for parsing LLM JSON output into a ThoughtAnalysisResult.
 */
interface IThoughtJsonParser {
    fun parse(jsonText: String): ThoughtAnalysisResult
}
