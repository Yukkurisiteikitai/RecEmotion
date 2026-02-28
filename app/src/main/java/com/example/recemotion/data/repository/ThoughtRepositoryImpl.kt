package com.example.recemotion.data.repository

import com.example.recemotion.data.db.ConversationTopicDao
import com.example.recemotion.data.db.ConversationTopicEntity
import com.example.recemotion.data.db.ThoughtAnalysisDao
import com.example.recemotion.data.db.ThoughtAnalysisEntity
import com.example.recemotion.data.db.ThoughtEntryDao
import com.example.recemotion.data.db.ThoughtEntryEntity
import com.example.recemotion.data.db.ToDoDao
import com.example.recemotion.data.db.ToDoEntity
import com.example.recemotion.domain.model.ConversationTopic
import com.example.recemotion.domain.model.ThoughtAnalysis
import com.example.recemotion.domain.model.ThoughtEntry
import com.example.recemotion.domain.model.ToDo
import com.example.recemotion.domain.repository.ThoughtRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Concrete implementation of [ThoughtRepository].
 * Delegates to Room DAOs; use-case and domain layers only see the interface.
 * Performs Entity ↔ Domain Model mapping at this boundary.
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

    override suspend fun getEntryById(id: Long): ThoughtEntry? =
        entryDao.getEntryById(id)?.toDomain()

    override suspend fun getLatestEntryForTopic(topicId: Long): ThoughtEntry? =
        entryDao.getEntriesByTopic(topicId).first().firstOrNull()?.toDomain()

    override fun getEntriesByTopic(topicId: Long): Flow<List<ThoughtEntry>> =
        entryDao.getEntriesByTopic(topicId).map { list -> list.map { it.toDomain() } }

    override fun getAllEntries(): Flow<List<ThoughtEntry>> =
        entryDao.getAllEntries().map { list -> list.map { it.toDomain() } }

    // ── Analysis operations ───────────────────────────────────────────────

    override suspend fun storeAnalysis(entryId: Long, analysisJson: String, timestamp: Long): Long =
        analysisDao.insert(
            ThoughtAnalysisEntity(entryId = entryId, analysisJson = analysisJson, createdAt = timestamp)
        )

    override suspend fun getAnalysisForEntry(entryId: Long): ThoughtAnalysis? =
        analysisDao.getAnalysisForEntry(entryId)?.toDomain()

    // ── Topic operations ──────────────────────────────────────────────────

    override suspend fun getActiveTopic(): ConversationTopic? =
        topicDao.getActiveTopic()?.toDomain()

    override suspend fun insertTopic(title: String, timestamp: Long): Long =
        topicDao.insertTopic(
            ConversationTopicEntity(title = title, createdAt = timestamp, updatedAt = timestamp)
        )

    override suspend fun getTopicById(id: Long): ConversationTopic? =
        topicDao.getTopicById(id)?.toDomain()

    override suspend fun updateTopicTimestamp(id: Long, timestamp: Long) {
        val topic = topicDao.getTopicById(id) ?: return
        topicDao.updateTopic(topic.copy(updatedAt = timestamp))
    }

    override suspend fun resolveTopic(id: Long, result: String, timestamp: Long) =
        topicDao.resolveTopic(id, result, timestamp)

    override fun getAllTopics(): Flow<List<ConversationTopic>> =
        topicDao.getAllTopics().map { list -> list.map { it.toDomain() } }

    // ── ToDo operations ───────────────────────────────────────────────────

    override fun getAllToDos(): Flow<List<ToDo>> =
        todoDao.getAllToDos().map { list -> list.map { it.toDomain() } }

    override suspend fun insertToDo(topicId: Long, description: String, timestamp: Long): Long =
        todoDao.insertToDo(ToDoEntity(topicId = topicId, description = description, createdAt = timestamp))

    override suspend fun updateToDoStatus(id: Long, isCompleted: Boolean) =
        todoDao.updateToDoStatus(id, isCompleted)

    // ── Mapping functions ─────────────────────────────────────────────────

    private fun ThoughtEntryEntity.toDomain() = ThoughtEntry(
        id = id,
        topicId = topicId,
        rawText = rawText,
        treeJson = treeJson,
        createdAt = createdAt
    )

    private fun ThoughtAnalysisEntity.toDomain() = ThoughtAnalysis(
        id = id,
        entryId = entryId,
        analysisJson = analysisJson,
        createdAt = createdAt
    )

    private fun ConversationTopicEntity.toDomain() = ConversationTopic(
        id = id,
        title = title,
        isResolved = isResolved,
        resolutionResult = resolutionResult,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ToDoEntity.toDomain() = ToDo(
        id = id,
        topicId = topicId,
        description = description,
        isCompleted = isCompleted,
        resultNotes = resultNotes,
        createdAt = createdAt
    )
}
