package com.dip.attendify.data.db.dao

import androidx.room.*
import com.dip.attendify.data.entity.SemesterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SemesterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(semester: SemesterEntity): Long

    @Update
    suspend fun update(semester: SemesterEntity)

    @Query("SELECT * FROM semesters WHERE isActive = 1 LIMIT 1")
    fun observeActiveSemester(): Flow<SemesterEntity?>

    @Query("SELECT * FROM semesters WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveSemester(): SemesterEntity?

    @Query("SELECT * FROM semesters WHERE isActive = 0 ORDER BY endDate DESC")
    fun observeArchivedSemesters(): Flow<List<SemesterEntity>>

    @Query("UPDATE semesters SET isActive = 0 WHERE id = :id")
    suspend fun archiveSemester(id: Int)
}