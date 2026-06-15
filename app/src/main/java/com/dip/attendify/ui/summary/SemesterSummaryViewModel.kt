package com.dip.attendify.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dip.attendify.core.SemesterManager
import com.dip.attendify.data.entity.*
import com.dip.attendify.data.repository.AttendanceRepository
import com.dip.attendify.data.repository.SubjectRepository
import com.dip.attendify.data.repository.TaskRepository
import com.dip.attendify.domain.AttendanceCalculator
import com.dip.attendify.domain.StreakCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class SubjectSummaryRow(
    val subjectName: String,
    val shortName:   String,
    val colorHex:    String,
    val present:     Int,
    val absent:      Int,
    val percentage:  Float,
    val tasksDone:   Int,
    val tasksPending: Int,
)

data class SemesterSummaryState(
    val isLoading:        Boolean              = true,
    val semesterName:     String               = "",
    val semesterDates:    String               = "",
    val overallPercent:   Float                = 0f,
    val totalPresent:     Int                  = 0,
    val totalAbsent:      Int                  = 0,
    val totalCancelled:   Int                  = 0,
    val totalTasksDone:   Int                  = 0,
    val totalTasksPending: Int                 = 0,
    val currentStreak:    Int                  = 0,
    val longestStreak:    Int                  = 0,
    val subjectRows:      List<SubjectSummaryRow> = emptyList(),
    val shareText:        String               = "",
)

@HiltViewModel
class SemesterSummaryViewModel @Inject constructor(
    private val semesterManager: SemesterManager,
    private val attendanceRepo:  AttendanceRepository,
    private val subjectRepo:     SubjectRepository,
    private val taskRepo:        TaskRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SemesterSummaryState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val semester = semesterManager.getActiveSemester()
            if (semester == null) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }

            val subjects    = subjectRepo.getAll()
            val subjectMap  = subjects.associateBy { it.id }
            val allRecords  = attendanceRepo.getAllForSemester(semester.id)
            val allTasks    = taskRepo.observeAllForSemester(semester.id).first()

            val statsRaw = allRecords
                .groupBy { it.subjectId }
                .map { (subjectId, records) ->
                    com.dip.attendify.data.db.dao.SubjectStatRaw(
                        subjectId = subjectId,
                        present   = records.count { it.status == AttendanceStatus.PRESENT },
                        absent    = records.count { it.status == AttendanceStatus.ABSENT },
                        cancelled = records.count { it.status == AttendanceStatus.CANCELLED },
                    )
                }

            // Use AttendanceCalculator for consistent stats computation
            val calc = AttendanceCalculator.compute(statsRaw, semester)
            val streak = StreakCalculator.compute(allRecords)

            val tasksBySubject = allTasks.groupBy { it.subjectId }

            val calcStatsMap = calc.subjectStats.associateBy { it.subjectId }
            val subjectRows = subjects.map { subject ->
                val s        = calcStatsMap[subject.id]
                val subTasks = tasksBySubject[subject.id] ?: emptyList()
                SubjectSummaryRow(
                    subjectName  = subject.name,
                    shortName    = subject.shortName,
                    colorHex     = subject.colorHex,
                    present      = s?.present    ?: 0,
                    absent       = s?.absent     ?: 0,
                    percentage   = s?.percentage ?: 0f,
                    tasksDone    = subTasks.count { it.status == TaskStatus.DONE },
                    tasksPending = subTasks.count { it.status != TaskStatus.DONE },
                )
            }.sortedBy { it.subjectName }

            val dateFmt   = DateTimeFormatter.ofPattern("dd MMM yyyy")
            val startDate = LocalDate.ofEpochDay(semester.startDate).format(dateFmt)
            val endDate   = LocalDate.ofEpochDay(semester.endDate).format(dateFmt)

            val shareText = buildShareText(
                semesterName   = semester.name,
                dates          = "$startDate – $endDate",
                overallPct     = calc.overallPercent,
                totalPresent   = calc.totalPresent,
                totalAbsent    = calc.totalAbsent,
                currentStreak  = streak.currentStreak,
                longestStreak  = streak.longestStreak,
                subjectRows    = subjectRows,
                tasksDone      = allTasks.count { it.status == TaskStatus.DONE },
                tasksPending   = allTasks.count { it.status != TaskStatus.DONE },
            )

            _state.update {
                SemesterSummaryState(
                    isLoading         = false,
                    semesterName      = semester.name,
                    semesterDates     = "$startDate – $endDate",
                    overallPercent    = calc.overallPercent,
                    totalPresent      = calc.totalPresent,
                    totalAbsent       = calc.totalAbsent,
                    totalCancelled    = calc.totalCancelled,
                    totalTasksDone    = allTasks.count { it.status == TaskStatus.DONE },
                    totalTasksPending = allTasks.count { it.status != TaskStatus.DONE },
                    currentStreak     = streak.currentStreak,
                    longestStreak     = streak.longestStreak,
                    subjectRows       = subjectRows,
                    shareText         = shareText,
                )
            }
        }
    }

    private fun buildShareText(
        semesterName:  String,
        dates:         String,
        overallPct:    Float,
        totalPresent:  Int,
        totalAbsent:   Int,
        currentStreak: Int,
        longestStreak: Int,
        subjectRows:   List<SubjectSummaryRow>,
        tasksDone:     Int,
        tasksPending:  Int,
    ): String = buildString {
        appendLine("📊 Attendify — $semesterName")
        appendLine("📅 $dates")
        appendLine()
        appendLine("Overall Attendance: ${overallPct.toInt()}%")
        appendLine("Present: $totalPresent  |  Absent: $totalAbsent")
        appendLine("🔥 Current Streak: $currentStreak days  |  Best: $longestStreak days")
        appendLine()
        appendLine("Subject Breakdown:")
        subjectRows.forEach { row ->
            val total = row.present + row.absent
            appendLine("• ${row.subjectName}: ${row.percentage.toInt()}% (${row.present}/${total})")
        }
        appendLine()
        appendLine("Tasks: $tasksDone done  |  $tasksPending pending")
        append("Generated by Attendify 2.0")
    }
}