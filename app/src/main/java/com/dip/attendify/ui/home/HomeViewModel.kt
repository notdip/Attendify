package com.dip.attendify.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dip.attendify.core.SemesterManager
import com.dip.attendify.data.db.dao.SubjectStatRaw
import com.dip.attendify.data.entity.SemesterEntity
import com.dip.attendify.data.entity.SubjectType
import com.dip.attendify.data.entity.SubjectEntity
import com.dip.attendify.data.entity.TaskEntity
import com.dip.attendify.data.entity.TimetableEntryEntity
import com.dip.attendify.data.entity.TimeSlotEntity
import com.dip.attendify.data.repository.AttendanceRepository
import com.dip.attendify.data.repository.SubjectRepository
import com.dip.attendify.data.repository.TaskRepository
import com.dip.attendify.data.repository.TimetableRepository
import com.dip.attendify.domain.AttendanceCalculator
import com.dip.attendify.domain.StreakCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// ─────────────────────────────────────────────
// Subject summary shown on a home card
// ─────────────────────────────────────────────

data class SubjectSummary(
    val subject:    SubjectEntity,
    val present:    Int,
    val absent:     Int,
    val percentage: Float,
    val isAtRisk:   Boolean,
    val canSkip:    Int,
    val mustAttend: Int,
    val lecStats:   com.dip.attendify.domain.AttendanceCalculator.SplitStats? = null,
    val labStats:   com.dip.attendify.domain.AttendanceCalculator.SplitStats? = null,
)

// ─────────────────────────────────────────────
// Screen state
// ─────────────────────────────────────────────

data class HomeScreenState(
    val isLoading:       Boolean              = true,
    val semester:        SemesterEntity?      = null,
    val overallPercent:  Float                = 0f,
    val totalPresent:    Int                  = 0,
    val totalAbsent:     Int                  = 0,
    val totalCancelled:  Int                  = 0,
    val subjectSummaries: List<SubjectSummary> = emptyList(),
    val todaySessions:   List<TodaySession>   = emptyList(),
    val pendingTaskCount: Int                 = 0,
    val streak:          Int                  = 0,
)

data class TodaySession(
    val subjectName:  String,
    val colorHex:     String,
    val startTime:    String,
    val endTime:      String,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val semesterManager:  SemesterManager,
    private val attendanceRepo:   AttendanceRepository,
    private val subjectRepo:      SubjectRepository,
    private val timetableRepo:    TimetableRepository,
    private val taskRepo:         TaskRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeScreenState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            semesterManager.activeSemester
                .flatMapLatest { semester ->
                    if (semester == null) {
                        flowOf(HomeScreenState(isLoading = false, semester = null))
                    } else {
                        buildHomeFlow(semester)
                    }
                }
                .collect { state -> _state.value = state }
        }
    }

    private fun buildHomeFlow(semester: SemesterEntity): Flow<HomeScreenState> {
        val statsFlow     = attendanceRepo.observeSubjectStats(semester.id)
        val subjectsFlow  = subjectRepo.observeAll()
        val pendingFlow   = taskRepo.observePending(semester.id)
        val timetableFlow = timetableRepo.observeFullTimetable(semester.id)

        val allAttendanceFlow = attendanceRepo.observeHeatmapStats(semester.id)

        return combine(statsFlow, subjectsFlow, pendingFlow, timetableFlow) { stats, subjects, pending, _ ->
            // Key stats by subjectId — subjects with no attendance yet won't appear here
            val statsMap = stats.associateBy { it.subjectId }

            // Drive summaries from the full subject list so subjects show even with 0 attendance
            val subjectHistoryMap = if (subjects.any { it.type == SubjectType.BOTH }) {
                attendanceRepo.getAllForSemester(semester.id)
                    .groupBy { it.subjectId }
            } else emptyMap()

            val summaries = subjects.map { subject ->
                val s = statsMap[subject.id]
                val present   = s?.present   ?: 0
                val absent    = s?.absent    ?: 0
                val cancelled = s?.cancelled ?: 0
                val total     = present + absent
                val pct       = if (total == 0) 0f else present.toFloat() / total * 100f
                val target    = semester.targetAttendancePercent.toFloat()
                val warning   = target - semester.warningBufferPercent
                val canSkip   = if (total == 0) 0 else
                    kotlin.math.max(0, (present * 100f / target - total).toInt())
                val mustAttend = if (pct >= target || total == 0) 0 else {
                    val num = target * total - 100f * present
                    val den = 100f - target
                    if (den <= 0f) Int.MAX_VALUE else kotlin.math.ceil(num / den).toInt()
                }
                val (lecStats, labStats) = if (subject.type == SubjectType.BOTH) {
                    AttendanceCalculator.computeSplit(
                        records       = subjectHistoryMap[subject.id] ?: emptyList(),
                        subjectType   = subject.type,
                        targetPercent = semester.targetAttendancePercent,
                        warningBuffer = semester.warningBufferPercent,
                    )
                } else Pair(null, null)

                SubjectSummary(
                    subject    = subject,
                    present    = present,
                    absent     = absent,
                    percentage = pct,
                    isAtRisk   = total > 0 && pct < warning,
                    canSkip    = canSkip,
                    mustAttend = mustAttend,
                    lecStats   = lecStats,
                    labStats   = labStats,
                )
            }.sortedBy { it.subject.name }

            // Overall stats still computed from attendance records only
            val calc = AttendanceCalculator.compute(stats, semester)

            val todaySessions = buildTodaySessions(semester)

            HomeScreenState(
                isLoading        = false,
                semester         = semester,
                overallPercent   = calc.overallPercent,
                totalPresent     = calc.totalPresent,
                totalAbsent      = calc.totalAbsent,
                totalCancelled   = calc.totalCancelled,
                subjectSummaries = summaries,
                todaySessions    = todaySessions,
                pendingTaskCount = pending.size,
                streak           = run {
                    val allRecords = attendanceRepo.getAllForSemester(semester.id)
                    StreakCalculator.compute(allRecords).currentStreak
                },
            )
        }
    }

    private suspend fun buildTodaySessions(semester: SemesterEntity): List<TodaySession> {
        val today     = LocalDate.now()
        val dayOfWeek = today.dayOfWeek.value
        val entries   = timetableRepo.getEntriesForDay(semester.id, dayOfWeek)
        val subjects  = subjectRepo.getAll().associateBy { it.id }
        val slots     = timetableRepo.getAllSlots().associateBy { it.id }

        return entries.mapNotNull { entry ->
            val subject   = subjects[entry.subjectId]   ?: return@mapNotNull null
            val startSlot = slots[entry.startSlotId]    ?: return@mapNotNull null
            val endSlot   = slots[entry.endSlotId]      ?: return@mapNotNull null
            TodaySession(
                subjectName = subject.name,
                colorHex    = subject.colorHex,
                startTime   = startSlot.startTime,
                endTime     = endSlot.endTime,
            )
        }
    }
}