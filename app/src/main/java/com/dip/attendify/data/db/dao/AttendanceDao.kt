package com.dip.attendify.data.db.dao

import androidx.room.*
import com.dip.attendify.data.entity.AttendanceEntity
import kotlinx.coroutines.flow.Flow

// Raw query result models — not entities, just data holders for aggregate queries
data class SubjectStatRaw(
    val subjectId: Int,
    val present: Int,
    val absent: Int,
    val cancelled: Int,
)

data class OverallStatRaw(
    val present: Int,
    val absent: Int,
    val cancelled: Int,
)

data class HeatStatRaw(
    val date: Long,
    val present: Int,
    val absent: Int,
)

@Dao
interface AttendanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attendance: AttendanceEntity): Long

    @Update
    suspend fun update(attendance: AttendanceEntity)

    @Query("DELETE FROM attendance WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("""
        SELECT * FROM attendance
        WHERE semesterId = :semId
        AND subjectId = :subjectId
        AND date = :date
        AND startSlotId = :startSlotId
        AND endSlotId = :endSlotId
        LIMIT 1
    """)
    suspend fun getExisting(
        semId: Int,
        subjectId: Int,
        date: Long,
        startSlotId: Int,
        endSlotId: Int,
    ): AttendanceEntity?

    @Query("SELECT * FROM attendance WHERE semesterId = :semId AND date = :date ORDER BY startSlotId")
    fun observeForDate(semId: Int, date: Long): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE semesterId = :semId AND date = :date ORDER BY startSlotId")
    suspend fun getForDate(semId: Int, date: Long): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE semesterId = :semId AND subjectId = :subjectId ORDER BY date ASC")
    fun observeSubjectHistory(semId: Int, subjectId: Int): Flow<List<AttendanceEntity>>

    @Query("""
        SELECT subjectId,
        SUM(CASE WHEN status = 'PRESENT'   THEN 1 ELSE 0 END) AS present,
        SUM(CASE WHEN status = 'ABSENT'    THEN 1 ELSE 0 END) AS absent,
        SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled
        FROM attendance
        WHERE semesterId = :semId
        GROUP BY subjectId
    """)
    fun observeSubjectStats(semId: Int): Flow<List<SubjectStatRaw>>

    @Query("""
        SELECT
        SUM(CASE WHEN status = 'PRESENT'   THEN 1 ELSE 0 END) AS present,
        SUM(CASE WHEN status = 'ABSENT'    THEN 1 ELSE 0 END) AS absent,
        SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled
        FROM attendance
        WHERE semesterId = :semId
    """)
    fun observeOverallStats(semId: Int): Flow<OverallStatRaw>

    @Query("""
        SELECT date,
        SUM(CASE WHEN status = 'PRESENT' THEN 1 ELSE 0 END) AS present,
        SUM(CASE WHEN status = 'ABSENT'  THEN 1 ELSE 0 END) AS absent
        FROM attendance
        WHERE semesterId = :semId
        GROUP BY date
    """)
    fun observeHeatmapStats(semId: Int): Flow<List<HeatStatRaw>>

    @Query("SELECT * FROM attendance WHERE semesterId = :semId AND date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getBetweenDates(semId: Int, from: Long, to: Long): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE semesterId = :semId ORDER BY date ASC")
    suspend fun getAllForSemester(semId: Int): List<AttendanceEntity>
}