package com.example.recemotion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ToDoDao {
    @Query("SELECT * FROM todo_items WHERE topic_id = :topicId ORDER BY created_at ASC")
    fun getToDosForTopic(topicId: Long): Flow<List<ToDoEntity>>

    @Query("SELECT * FROM todo_items ORDER BY created_at ASC")
    fun getAllToDos(): Flow<List<ToDoEntity>>

    @Query("SELECT * FROM todo_items WHERE topic_id = :topicId ORDER BY created_at ASC")
    suspend fun getToDosForTopicOneShot(topicId: Long): List<ToDoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToDo(todo: ToDoEntity): Long

    @Update
    suspend fun updateToDo(todo: ToDoEntity)

    @Query("UPDATE todo_items SET is_completed = :isCompleted WHERE id = :id")
    suspend fun updateToDoStatus(id: Long, isCompleted: Boolean)

    @Query("DELETE FROM todo_items WHERE id = :id")
    suspend fun deleteToDo(id: Long)
}
