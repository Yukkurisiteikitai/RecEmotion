package com.example.recemotion.domain.repository

import com.example.recemotion.domain.model.ConversationTopic
import com.example.recemotion.domain.model.ThoughtAnalysis
import com.example.recemotion.domain.model.ThoughtEntry
import com.example.recemotion.domain.model.ToDo
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for all persistent data operations.
 * Use cases depend on this interface, not concrete DAO implementations.
 */
interface ThoughtRepository {

    // ── Entry operations ──────────────────────────────────────────────────

    suspend fun storeEntry(topicId: Long?, rawText: String, treeJson: String, timestamp: Long): Long

    suspend fun updateEntry(id: Long, treeJson: String)

    suspend fun getEntryById(id: Long): ThoughtEntry?

    suspend fun getLatestEntryForTopic(topicId: Long): ThoughtEntry?

    fun getEntriesByTopic(topicId: Long): Flow<List<ThoughtEntry>>

    fun getAllEntries(): Flow<List<ThoughtEntry>>

    // ── Analysis operations ───────────────────────────────────────────────

    suspend fun storeAnalysis(entryId: Long, analysisJson: String, timestamp: Long): Long

    suspend fun getAnalysisForEntry(entryId: Long): ThoughtAnalysis?

    // ── Topic operations ──────────────────────────────────────────────────

    suspend fun getActiveTopic(): ConversationTopic?

    suspend fun insertTopic(title: String, timestamp: Long): Long

    suspend fun getTopicById(id: Long): ConversationTopic?

    suspend fun updateTopicTimestamp(id: Long, timestamp: Long)

    suspend fun resolveTopic(id: Long, result: String, timestamp: Long)

    fun getAllTopics(): Flow<List<ConversationTopic>>

    // ── ToDo operations ───────────────────────────────────────────────────

    fun getAllToDos(): Flow<List<ToDo>>

    suspend fun insertToDo(topicId: Long, description: String, timestamp: Long): Long

    suspend fun updateToDoStatus(id: Long, isCompleted: Boolean)
}
