package com.dip.attendify.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ─────────────────────────────────────────────
// Semester
// ─────────────────────────────────────────────

@Entity(tableName = "semesters")
data class SemesterEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val startDate: Long,                    // epoch day
    val endDate: Long,                      // epoch day
    val targetAttendancePercent: Int = 75,
    val warningBufferPercent: Int = 5,
    val isActive: Boolean = true,
)

// ─────────────────────────────────────────────
// Subject
// ─────────────────────────────────────────────

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val shortName: String,                  // "OOP", "M3" — for chips and heatmap labels
    val colorHex: String,                   // "#FF5722" — per-subject color token
    val type: SubjectType,
)

enum class SubjectType { LECTURE, LAB, BOTH }

// ─────────────────────────────────────────────
// Time Slot
// ─────────────────────────────────────────────

@Entity(tableName = "time_slots")
data class TimeSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTime: String,                  // "09:00"
    val endTime: String,                    // "10:00"
    val slotOrder: Int,
)

// ─────────────────────────────────────────────
// Timetable Entry
// ─────────────────────────────────────────────

@Entity(
    tableName = "timetable_entries",
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("semesterId"), Index("subjectId")],
)
data class TimetableEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val semesterId: Int,
    val dayOfWeek: Int,                     // 1 = Monday … 6 = Saturday
    val subjectId: Int,
    val startSlotId: Int,
    val endSlotId: Int,
)

// ─────────────────────────────────────────────
// Attendance
// ─────────────────────────────────────────────

@Entity(
    tableName = "attendance",
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("semesterId"), Index("subjectId"), Index("date")],
)
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val semesterId: Int,
    val subjectId: Int,
    val date: Long,                         // epoch day
    val dayOfWeek: Int,
    val startSlotId: Int,
    val endSlotId: Int,
    val sessionType: SessionType = SessionType.REGULAR,
    val status: AttendanceStatus,
    val note: String? = null,
)

enum class AttendanceStatus { PRESENT, ABSENT, CANCELLED }

enum class SessionType {
    REGULAR,    // normal timetable class
    PROXY,      // replacement / cover lecture
    EXTRA,      // bonus session outside normal schedule
    CANCELLED,  // class was cancelled for the day
}

// ─────────────────────────────────────────────
// Task  (termwork to-do)
// ─────────────────────────────────────────────

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("semesterId"), Index("subjectId")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val semesterId: Int,
    val subjectId: Int,
    val title: String,
    val type: TaskType,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueDate: Long? = null,              // epoch day, nullable = no deadline
    val status: TaskStatus = TaskStatus.PENDING,
    val note: String? = null,
)

enum class TaskType {
    ASSIGNMENT,
    LAB_REPORT,
    CASE_STUDY,
    PRESENTATION,
    VIVA,
    PROJECT,
    OTHER,
}

enum class TaskPriority { LOW, MEDIUM, HIGH }

enum class TaskStatus { PENDING, IN_PROGRESS, DONE }

// ─────────────────────────────────────────────
// Academic Event  (exams, deadlines, holidays)
// ─────────────────────────────────────────────

@Entity(
    tableName = "academic_events",
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("semesterId")],
)
data class AcademicEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val semesterId: Int,
    val title: String,
    val date: Long,                         // epoch day
    val endDate: Long? = null,              // for multi-day events (exam week etc.)
    val type: AcademicEventType,
    val note: String? = null,
)

enum class AcademicEventType {
    EXAM,
    SUBMISSION_DEADLINE,
    HOLIDAY,
    SEMESTER_BREAK,
    OTHER,
}