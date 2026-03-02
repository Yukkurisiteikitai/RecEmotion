package com.example.recemotion.domain.service

import com.example.recemotion.domain.model.ThoughtStructure

/**
 * Domain interface for serializing and deserializing ThoughtStructure to/from JSON.
 */
interface IThoughtStructureSerializer {
    fun toJson(structure: ThoughtStructure): String
    fun fromJson(json: String): ThoughtStructure
}
