package com.dip.attendify.data.repository

import com.dip.attendify.data.db.dao.*
import com.dip.attendify.data.entity.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// ─────────────────────────────────────────────
// Semester
// ─────────────────────────────────────────────

interface SemesterRepository {
    fun observeActiveSemester(): Flow<SemesterEntity?>
    suspend fun getActiveSemester(): SemesterEntity?
    fun observeArchivedSemesters(): Flow<List<SemesterEntity>>
    suspend fun createSemester(semester: SemesterEntity): Long
    suspend fun archiveCurrent()
    suspend fun updateSemester(semester: SemesterEntity)
}

class SemesterRepositoryImpl @Inject constructor(
    private val dao: SemesterDao,
) : SemesterRepository {
    override fun observeActiveSemester()               = dao.observeActiveSemester()
    override suspend fun getActiveSemester()           = dao.getActiveSemester()
    override fun observeArchivedSemesters()            = dao.observeArchivedSemesters()
    override suspend fun createSemester(s: SemesterEntity) = dao.insert(s)
    override suspend fun archiveCurrent() {
        dao.getActiveSemester()?.let { dao.archiveSemester(it.id) }
    }
    override suspend fun updateSemester(s: SemesterEntity) = dao.update(s)
}

// ─────────────────────────────────────────────
// Subject
// ─────────────────────────────────────────────

interface SubjectRepository {
    fun observeAll(): Flow<List<SubjectEntity>>
    suspend fun getAll(): List<SubjectEntity>
    suspend fun getById(id: Int): SubjectEntity?
    suspend fun insert(subject: SubjectEntity): Long
    suspend fun update(subject: SubjectEntity)
    suspend fun deleteById(id: Int)
}

class SubjectRepositoryImpl @Inject constructor(
    private val dao: SubjectDao,
) : SubjectRepository {
    override fun observeAll()                         = dao.observeAll()
    override suspend fun getAll()                     = dao.getAll()
    override suspend fun getById(id: Int)             = dao.getById(id)
    override suspend fun insert(s: SubjectEntity)     = dao.insert(s)
    override suspend fun update(s: SubjectEntity)     = dao.update(s)
    override suspend fun deleteById(id: Int)          = dao.deleteById(id)
}

// ─────────────────────────────────────────────
// Timetable  (slots + entries)
// ─────────────────────────────────────────────

interface TimetableRepository {
    suspend fun getAllSlots(): List<TimeSlotEntity>
    fun observeAllSlots(): Flow<List<TimeSlotEntity>>
    suspend fun insertAllSlots(slots: List<TimeSlotEntity>)
    suspend fun clearAllSlots()
    suspend fun updateSlot(slot: TimeSlotEntity)

    suspend fun getEntriesForDay(semId: Int, day: Int): List<TimetableEntryEntity>
    fun observeEntriesForDay(semId: Int, day: Int): Flow<List<TimetableEntryEntity>>
    fun observeFullTimetable(semId: Int): Flow<List<TimetableEntryEntity>>
    suspend fun insertEntry(entry: TimetableEntryEntity): Long
    suspend fun insertAll(entries: List<TimetableEntryEntity>)
    suspend fun deleteEntryById(id: Int)
    suspend fun clearDay(semId: Int, day: Int)
    suspend fun clearSemester(semId: Int)
    suspend fun getEntryForCell(semId: Int, day: Int, slotId: Int): TimetableEntryEntity?
}

class TimetableRepositoryImpl @Inject constructor(
    private val slotDao: TimeSlotDao,
    private val ttDao: TimetableDao,
) : TimetableRepository {
    override suspend fun getAllSlots()                               = slotDao.getAll()
    override fun observeAllSlots()                                  = slotDao.observeAll()
    override suspend fun insertAllSlots(s: List<TimeSlotEntity>)    = slotDao.insertAll(s)
    override suspend fun clearAllSlots()                            = slotDao.clearAll()
    override suspend fun updateSlot(s: TimeSlotEntity)              = slotDao.update(s)

    override suspend fun getEntriesForDay(semId: Int, day: Int)     = ttDao.getEntriesForDay(semId, day)
    override fun observeEntriesForDay(semId: Int, day: Int)         = ttDao.observeEntriesForDay(semId, day)
    override fun observeFullTimetable(semId: Int)                   = ttDao.observeFullTimetable(semId)
    override suspend fun insertEntry(e: TimetableEntryEntity)       = ttDao.insert(e)
    override suspend fun insertAll(e: List<TimetableEntryEntity>)   = ttDao.insertAll(e)
    override suspend fun deleteEntryById(id: Int)                   = ttDao.deleteById(id)
    override suspend fun clearDay(semId: Int, day: Int)             = ttDao.clearDay(semId, day)
    override suspend fun clearSemester(semId: Int)                  = ttDao.clearSemester(semId)
    override suspend fun getEntryForCell(semId: Int, day: Int, slotId: Int) =
        ttDao.getEntryForCell(semId, day, slotId)
}

// ─────────────────────────────────────────────
// Attendance
// ─────────────────────────────────────────────

interface AttendanceRepository {
    suspend fun upsert(attendance: AttendanceEntity): Long
    suspend fun update(attendance: AttendanceEntity)
    suspend fun deleteById(id: Int)
    suspend fun getExisting(semId: Int, subjectId: Int, date: Long, startSlotId: Int, endSlotId: Int): AttendanceEntity?
    fun observeForDate(semId: Int, date: Long): Flow<List<AttendanceEntity>>
    suspend fun getForDate(semId: Int, date: Long): List<AttendanceEntity>
    fun observeSubjectHistory(semId: Int, subjectId: Int): Flow<List<AttendanceEntity>>
    fun observeSubjectStats(semId: Int): Flow<List<SubjectStatRaw>>
    fun observeOverallStats(semId: Int): Flow<OverallStatRaw>
    fun observeHeatmapStats(semId: Int): Flow<List<HeatStatRaw>>
    suspend fun getBetweenDates(semId: Int, from: Long, to: Long): List<AttendanceEntity>
    suspend fun getAllForSemester(semId: Int): List<AttendanceEntity>
}

class AttendanceRepositoryImpl @Inject constructor(
    private val dao: AttendanceDao,
) : AttendanceRepository {
    override suspend fun upsert(a: AttendanceEntity)    = dao.insert(a)
    override suspend fun update(a: AttendanceEntity)    = dao.update(a)
    override suspend fun deleteById(id: Int)            = dao.deleteById(id)
    override suspend fun getExisting(semId: Int, subjectId: Int, date: Long, startSlotId: Int, endSlotId: Int) =
        dao.getExisting(semId, subjectId, date, startSlotId, endSlotId)
    override fun observeForDate(semId: Int, date: Long) = dao.observeForDate(semId, date)
    override suspend fun getForDate(semId: Int, date: Long) = dao.getForDate(semId, date)
    override fun observeSubjectHistory(semId: Int, subjectId: Int) = dao.observeSubjectHistory(semId, subjectId)
    override fun observeSubjectStats(semId: Int)        = dao.observeSubjectStats(semId)
    override fun observeOverallStats(semId: Int)        = dao.observeOverallStats(semId)
    override fun observeHeatmapStats(semId: Int)        = dao.observeHeatmapStats(semId)
    override suspend fun getBetweenDates(semId: Int, from: Long, to: Long) = dao.getBetweenDates(semId, from, to)
    override suspend fun getAllForSemester(semId: Int)  = dao.getAllForSemester(semId)
}

// ─────────────────────────────────────────────
// Task
// ─────────────────────────────────────────────

interface TaskRepository {
    suspend fun insert(task: TaskEntity): Long
    suspend fun update(task: TaskEntity)
    suspend fun deleteById(id: Int)
    suspend fun updateStatus(id: Int, status: TaskStatus)
    fun observeAllForSemester(semId: Int): Flow<List<TaskEntity>>
    fun observeForSubject(semId: Int, subjectId: Int): Flow<List<TaskEntity>>
    fun observePending(semId: Int): Flow<List<TaskEntity>>
}

class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
) : TaskRepository {
    override suspend fun insert(t: TaskEntity)                = dao.insert(t)
    override suspend fun update(t: TaskEntity)                = dao.update(t)
    override suspend fun deleteById(id: Int)                  = dao.deleteById(id)
    override suspend fun updateStatus(id: Int, s: TaskStatus) = dao.updateStatus(id, s.name)
    override fun observeAllForSemester(semId: Int)            = dao.observeAllForSemester(semId)
    override fun observeForSubject(semId: Int, subjectId: Int)= dao.observeForSubject(semId, subjectId)
    override fun observePending(semId: Int)                   = dao.observePending(semId)
}

// ─────────────────────────────────────────────
// AcademicEvent
// ─────────────────────────────────────────────

interface AcademicEventRepository {
    suspend fun insert(event: AcademicEventEntity): Long
    suspend fun update(event: AcademicEventEntity)
    suspend fun deleteById(id: Int)
    fun observeAll(semId: Int): Flow<List<AcademicEventEntity>>
    suspend fun getForDate(semId: Int, date: Long): List<AcademicEventEntity>
    fun observeBetweenDates(semId: Int, from: Long, to: Long): Flow<List<AcademicEventEntity>>
}

class AcademicEventRepositoryImpl @Inject constructor(
    private val dao: AcademicEventDao,
) : AcademicEventRepository {
    override suspend fun insert(e: AcademicEventEntity)              = dao.insert(e)
    override suspend fun update(e: AcademicEventEntity)              = dao.update(e)
    override suspend fun deleteById(id: Int)                         = dao.deleteById(id)
    override fun observeAll(semId: Int)                              = dao.observeAll(semId)
    override suspend fun getForDate(semId: Int, date: Long)          = dao.getForDate(semId, date)
    override fun observeBetweenDates(semId: Int, from: Long, to: Long) = dao.observeBetweenDates(semId, from, to)
}