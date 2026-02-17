package com.example.recemotion.data.repository

import com.example.recemotion.data.db.ThoughtAnalysisDao
import com.example.recemotion.data.db.ThoughtAnalysisEntity
import com.example.recemotion.data.db.ThoughtEntryDao
import com.example.recemotion.data.db.ThoughtEntryEntity

class ThoughtRepository(
    private val entryDao: ThoughtEntryDao,
    private val analysisDao: ThoughtAnalysisDao
) {

    suspend fun storeEntry(rawText: String, treeJson: String, timestamp: Long): Long {
        val entry = ThoughtEntryEntity(
            rawText = rawText,
            treeJson = treeJson,
            createdAt = timestamp
        )
        return entryDao.insert(entry)
    }

    suspend fun storeAnalysis(entryId: Long, analysisJson: String, timestamp: Long): Long {
        val analysis = ThoughtAnalysisEntity(
            entryId = entryId,
            analysisJson = analysisJson,
            createdAt = timestamp
        )
        return analysisDao.insert(analysis)
    }
}
