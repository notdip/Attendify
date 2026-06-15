package com.dip.attendify.data.db.dao

import androidx.room.*
import com.dip.attendify.data.entity.AcademicEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AcademicEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AcademicEventEntity): Long

    @Update
    suspend fun update(event: AcademicEventEntity)

    @Query("DELETE FROM academic_events WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM academic_events WHERE semesterId = :semId ORDER BY date ASC")
    fun observeAll(semId: Int): Flow<List<AcademicEventEntity>>

    @Query("SELECT * FROM academic_events WHERE semesterId = :semId AND date = :date")
    suspend fun getForDate(semId: Int, date: Long): List<AcademicEventEntity>

    @Query("SELECT * FROM academic_events WHERE semesterId = :semId AND date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeBetweenDates(semId: Int, from: Long, to: Long): Flow<List<AcademicEventEntity>>
}