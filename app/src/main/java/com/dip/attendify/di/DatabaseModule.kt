package com.dip.attendify.di

import android.content.Context
import androidx.room.Room
import com.dip.attendify.data.db.AppDatabase
import com.dip.attendify.data.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "attendify.db",
        ).build()

    @Provides fun provideSemesterDao(db: AppDatabase): SemesterDao         = db.semesterDao()
    @Provides fun provideSubjectDao(db: AppDatabase): SubjectDao            = db.subjectDao()
    @Provides fun provideTimeSlotDao(db: AppDatabase): TimeSlotDao          = db.timeSlotDao()
    @Provides fun provideTimetableDao(db: AppDatabase): TimetableDao        = db.timetableDao()
    @Provides fun provideAttendanceDao(db: AppDatabase): AttendanceDao      = db.attendanceDao()
    @Provides fun provideTaskDao(db: AppDatabase): TaskDao                  = db.taskDao()
    @Provides fun provideAcademicEventDao(db: AppDatabase): AcademicEventDao = db.academicEventDao()
}