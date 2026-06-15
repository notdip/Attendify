package com.dip.attendify.data.db.dao

import androidx.room.*
import com.dip.attendify.data.entity.TimeSlotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeSlotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(slot: TimeSlotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(slots: List<TimeSlotEntity>)

    @Update
    suspend fun update(slot: TimeSlotEntity)

    @Query("SELECT * FROM time_slots ORDER BY slotOrder ASC")
    fun observeAll(): Flow<List<TimeSlotEntity>>

    @Query("SELECT * FROM time_slots ORDER BY slotOrder ASC")
    suspend fun getAll(): List<TimeSlotEntity>

    @Query("SELECT * FROM time_slots WHERE id = :id")
    suspend fun getById(id: Int): TimeSlotEntity?

    @Query("DELETE FROM time_slots")
    suspend fun clearAll()
}