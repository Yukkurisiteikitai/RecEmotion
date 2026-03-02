package com.example.recemotion.domain.service

import com.example.recemotion.domain.model.ThoughtStructure

/**
 * Domain interface for building LLM prompts from a ThoughtStructure.
 */
interface IPromptBuilder {
    fun build(structure: ThoughtStructure, emotionContext: String? = null): String
}
