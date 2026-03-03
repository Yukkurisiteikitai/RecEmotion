package com.example.recemotion.data.repository

import com.example.recemotion.data.db.TaskDao
import com.example.recemotion.data.db.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao
) {

    suspend fun createTask(task: TaskEntity): Long = taskDao.insert(task)

    suspend fun updateTask(task: TaskEntity) = taskDao.update(task)

    suspend fun deleteTask(task: TaskEntity) = taskDao.delete(task)

    suspend fun getCurrentTask(): TaskEntity? = taskDao.getCurrentTask()

    suspend fun getTaskById(taskId: Long): TaskEntity? = taskDao.getTaskById(taskId)

    fun getTaskFlow(id: Long): Flow<TaskEntity?> =
        taskDao.getAllTasks().map { tasks -> tasks.firstOrNull { it.id == id } }

    fun getTasksByStatus(status: String): Flow<List<TaskEntity>> =
        taskDao.getTasksByStatus(status)

    fun getAllTasks(): Flow<List<TaskEntity>> = taskDao.getAllTasks()

    suspend fun updateTaskStatus(taskId: Long, status: String): Boolean {
        val current = taskDao.getTaskById(taskId) ?: return false
        taskDao.update(current.copy(status = status))
        return true
    }
}
