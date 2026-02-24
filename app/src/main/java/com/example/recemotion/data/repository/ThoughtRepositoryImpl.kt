package com.example.recemotion.data.repository

import com.example.recemotion.data.db.ConversationTopicDao
import com.example.recemotion.data.db.ConversationTopicEntity
import com.example.recemotion.data.db.ThoughtAnalysisDao
import com.example.recemotion.data.db.ThoughtAnalysisEntity
import com.example.recemotion.data.db.ThoughtEntryDao
import com.example.recemotion.data.db.ThoughtEntryEntity
import com.example.recemotion.data.db.ToDoDao
import com.example.recemotion.data.db.ToDoEntity
import com.example.recemotion.domain.repository.ThoughtRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Concrete implementation of [ThoughtRepository].
 * Delegates to Room DAOs; use-case and domain layers only see the interface.
 */
class ThoughtRepositoryImpl @Inject constructor(
    private val entryDao: ThoughtEntryDao,
    private val analysisDao: ThoughtAnalysisDao,
    private val topicDao: ConversationTopicDao,
    private val todoDao: ToDoDao
) : ThoughtRepository {

    // ── Entry operations ──────────────────────────────────────────────────

    override suspend fun storeEntry(
        topicId: Long?,
        rawText: String,
        treeJson: String,
        timestamp: Long
    ): Long = entryDao.insertEntry(
        ThoughtEntryEntity(topicId = topicId, rawText = rawText, treeJson = treeJson, createdAt = timestamp)
    )

    override suspend fun updateEntry(id: Long, treeJson: String) {
        val existing = entryDao.getEntryById(id) ?: return
        entryDao.updateEntry(existing.copy(treeJson = treeJson))
    }

    override suspend fun getEntryById(id: Long): ThoughtEntryEntity? =
        entryDao.getEntryById(id)

    override suspend fun getLatestEntryForTopic(topicId: Long): ThoughtEntryEntity? =
        entryDao.getEntriesByTopic(topicId).first().firstOrNull()

    override fun getEntriesByTopic(topicId: Long): Flow<List<ThoughtEntryEntity>> =
        entryDao.getEntriesByTopic(topicId)

    override fun getAllEntries(): Flow<List<ThoughtEntryEntity>> =
        entryDao.getAllEntries()

    // ── Analysis operations ───────────────────────────────────────────────

    override suspend fun storeAnalysis(entryId: Long, analysisJson: String, timestamp: Long): Long =
        analysisDao.insert(
            ThoughtAnalysisEntity(entryId = entryId, analysisJson = analysisJson, createdAt = timestamp)
        )

    override suspend fun getAnalysisForEntry(entryId: Long): ThoughtAnalysisEntity? =
        analysisDao.getAnalysisForEntry(entryId)

    // ── Topic operations ──────────────────────────────────────────────────

    override suspend fun getActiveTopic(): ConversationTopicEntity? =
        topicDao.getActiveTopic()

    override suspend fun insertTopic(title: String, timestamp: Long): Long =
        topicDao.insertTopic(
            ConversationTopicEntity(title = title, createdAt = timestamp, updatedAt = timestamp)
        )

    override suspend fun getTopicById(id: Long): ConversationTopicEntity? =
        topicDao.getTopicById(id)

    override suspend fun updateTopicTimestamp(id: Long, timestamp: Long) {
        val topic = topicDao.getTopicById(id) ?: return
        topicDao.updateTopic(topic.copy(updatedAt = timestamp))
    }

    override suspend fun resolveTopic(id: Long, result: String, timestamp: Long) =
        topicDao.resolveTopic(id, result, timestamp)

    override fun getAllTopics(): Flow<List<ConversationTopicEntity>> =
        topicDao.getAllTopics()

    // ── ToDo operations ───────────────────────────────────────────────────

    override fun getAllToDos(): Flow<List<ToDoEntity>> =
        todoDao.getAllToDos()

    override suspend fun insertToDo(topicId: Long, description: String, timestamp: Long): Long =
        todoDao.insertToDo(ToDoEntity(topicId = topicId, description = description, createdAt = timestamp))

    override suspend fun updateToDoStatus(id: Long, isCompleted: Boolean) =
        todoDao.updateToDoStatus(id, isCompleted)
}
