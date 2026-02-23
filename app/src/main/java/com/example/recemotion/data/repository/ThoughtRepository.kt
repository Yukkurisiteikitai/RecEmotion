package com.example.recemotion.data.repository

import com.example.recemotion.data.db.ConversationTopicDao
import com.example.recemotion.data.db.ThoughtAnalysisDao
import com.example.recemotion.data.db.ThoughtAnalysisEntity
import com.example.recemotion.data.db.ThoughtEntryDao
import com.example.recemotion.data.db.ThoughtEntryEntity

class ThoughtRepository(
    private val entryDao: ThoughtEntryDao,
    private val analysisDao: ThoughtAnalysisDao,
    private val topicDao: ConversationTopicDao
) {

    suspend fun storeEntry(topicId: Long?, rawText: String, treeJson: String, timestamp: Long): Long {
        val entry = ThoughtEntryEntity(
            topicId = topicId,
            rawText = rawText,
            treeJson = treeJson,
            createdAt = timestamp
        )
        return entryDao.insertEntry(entry)
    }

    suspend fun updateEntry(entry: ThoughtEntryEntity) {
        entryDao.updateEntry(entry)
    }

    suspend fun getEntryById(id: Long): ThoughtEntryEntity? {
        return entryDao.getEntryById(id)
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
