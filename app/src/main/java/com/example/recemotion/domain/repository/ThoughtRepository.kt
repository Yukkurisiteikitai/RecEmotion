package com.example.recemotion.domain.repository

import com.example.recemotion.data.db.ConversationTopicEntity
import com.example.recemotion.data.db.ThoughtAnalysisEntity
import com.example.recemotion.data.db.ThoughtEntryEntity
import com.example.recemotion.data.db.ToDoEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for all persistent data operations.
 * Use cases depend on this interface, not concrete DAO implementations.
 */
interface ThoughtRepository {

    // ── Entry operations ──────────────────────────────────────────────────

    suspend fun storeEntry(topicId: Long?, rawText: String, treeJson: String, timestamp: Long): Long

    suspend fun updateEntry(id: Long, treeJson: String)

    suspend fun getEntryById(id: Long): ThoughtEntryEntity?

    suspend fun getLatestEntryForTopic(topicId: Long): ThoughtEntryEntity?

    fun getEntriesByTopic(topicId: Long): Flow<List<ThoughtEntryEntity>>

    fun getAllEntries(): Flow<List<ThoughtEntryEntity>>

    // ── Analysis operations ───────────────────────────────────────────────

    suspend fun storeAnalysis(entryId: Long, analysisJson: String, timestamp: Long): Long

    suspend fun getAnalysisForEntry(entryId: Long): ThoughtAnalysisEntity?

    // ── Topic operations ──────────────────────────────────────────────────

    suspend fun getActiveTopic(): ConversationTopicEntity?

    suspend fun insertTopic(title: String, timestamp: Long): Long

    suspend fun getTopicById(id: Long): ConversationTopicEntity?

    suspend fun updateTopicTimestamp(id: Long, timestamp: Long)

    suspend fun resolveTopic(id: Long, result: String, timestamp: Long)

    fun getAllTopics(): Flow<List<ConversationTopicEntity>>

    // ── ToDo operations ───────────────────────────────────────────────────

    fun getAllToDos(): Flow<List<ToDoEntity>>

    suspend fun insertToDo(topicId: Long, description: String, timestamp: Long): Long

    suspend fun updateToDoStatus(id: Long, isCompleted: Boolean)
}
