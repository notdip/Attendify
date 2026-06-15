package com.dip.attendify.data.db.dao

import androidx.room.*
import com.dip.attendify.data.entity.TimetableEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TimetableEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<TimetableEntryEntity>)

    @Query("SELECT * FROM timetable_entries WHERE semesterId = :semId AND dayOfWeek = :day ORDER BY startSlotId ASC")
    suspend fun getEntriesForDay(semId: Int, day: Int): List<TimetableEntryEntity>

    @Query("SELECT * FROM timetable_entries WHERE semesterId = :semId AND dayOfWeek = :day ORDER BY startSlotId ASC")
    fun observeEntriesForDay(semId: Int, day: Int): Flow<List<TimetableEntryEntity>>

    @Query("SELECT * FROM timetable_entries WHERE semesterId = :semId ORDER BY dayOfWeek, startSlotId")
    fun observeFullTimetable(semId: Int): Flow<List<TimetableEntryEntity>>

    @Query("""
        SELECT * FROM timetable_entries
        WHERE semesterId = :semId
        AND dayOfWeek = :day
        AND startSlotId <= :slotId AND endSlotId >= :slotId
        LIMIT 1
    """)
    suspend fun getEntryForCell(semId: Int, day: Int, slotId: Int): TimetableEntryEntity?

    @Query("DELETE FROM timetable_entries WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM timetable_entries WHERE semesterId = :semId AND dayOfWeek = :day")
    suspend fun clearDay(semId: Int, day: Int)

    @Query("DELETE FROM timetable_entries WHERE semesterId = :semId")
    suspend fun clearSemester(semId: Int)
}