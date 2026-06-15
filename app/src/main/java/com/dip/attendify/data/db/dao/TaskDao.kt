package com.dip.attendify.data.db.dao

import androidx.room.*
import com.dip.attendify.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String)

    @Query("SELECT * FROM tasks WHERE semesterId = :semId ORDER BY dueDate ASC, priority DESC")
    fun observeAllForSemester(semId: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE semesterId = :semId AND subjectId = :subjectId ORDER BY dueDate ASC")
    fun observeForSubject(semId: Int, subjectId: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE semesterId = :semId AND status != 'DONE' ORDER BY dueDate ASC")
    fun observePending(semId: Int): Flow<List<TaskEntity>>
}