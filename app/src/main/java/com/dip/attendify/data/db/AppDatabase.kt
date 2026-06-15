package com.dip.attendify.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dip.attendify.data.db.dao.*
import com.dip.attendify.data.entity.*

@Database(
    entities = [
        SemesterEntity::class,
        SubjectEntity::class,
        TimeSlotEntity::class,
        TimetableEntryEntity::class,
        AttendanceEntity::class,
        TaskEntity::class,
        AcademicEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun semesterDao(): SemesterDao
    abstract fun subjectDao(): SubjectDao
    abstract fun timeSlotDao(): TimeSlotDao
    abstract fun timetableDao(): TimetableDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun taskDao(): TaskDao
    abstract fun academicEventDao(): AcademicEventDao
}