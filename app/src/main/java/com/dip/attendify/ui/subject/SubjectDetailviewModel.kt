package com.dip.attendify.ui.subject

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dip.attendify.core.SemesterManager
import com.dip.attendify.data.entity.*
import com.dip.attendify.data.repository.AttendanceRepository
import com.dip.attendify.data.repository.SubjectRepository
import com.dip.attendify.data.repository.TaskRepository
import com.dip.attendify.domain.AttendanceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

// ─────────────────────────────────────────────
// Chart data models
// ─────────────────────────────────────────────

// One point on the trend line — cumulative % at that date
data class TrendPoint(
    val date:       LocalDate,
    val percentage: Float,
)

// One bar group per week — present + absent counts
data class WeekBar(
    val weekLabel: String,   // "W1", "W2" etc.
    val present:   Int,
    val absent:    Int,
)

// ─────────────────────────────────────────────
// Screen state
// ─────────────────────────────────────────────

data class SubjectDetailState(
    val isLoading:   Boolean                = true,
    val subject:     SubjectEntity?         = null,
    val present:     Int                    = 0,
    val absent:      Int                    = 0,
    val cancelled:   Int                    = 0,
    val percentage:  Float                  = 0f,
    val isAtRisk:    Boolean                = false,
    val canSkip:     Int                    = 0,
    val mustAttend:  Int                    = 0,
    val target:      Int                    = 75,
    val lecStats:    AttendanceCalculator.SplitStats? = null,
    val labStats:    AttendanceCalculator.SplitStats? = null,
    val trendPoints: List<TrendPoint>       = emptyList(),
    val weekBars:    List<WeekBar>          = emptyList(),
    val tasks:       List<TaskEntity>       = emptyList(),
    val sessionHistory: List<AttendanceEntity> = emptyList(),
    // Task form
    val showTaskForm: Boolean               = false,
    val taskForm:     TaskFormState         = TaskFormState(),
)

data class TaskFormState(
    val title:    String       = "",
    val type:     TaskType     = TaskType.ASSIGNMENT,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueDate:  LocalDate?   = null,
    val note:     String       = "",
) {
    val canSave: Boolean get() = title.isNotBlank()
}

@HiltViewModel
class SubjectDetailViewModel @Inject constructor(
    savedStateHandle:    SavedStateHandle,
    private val semesterManager:  SemesterManager,
    private val subjectRepo:      SubjectRepository,
    private val attendanceRepo:   AttendanceRepository,
    private val taskRepo:         TaskRepository,
) : ViewModel() {

    private val subjectId: Int = checkNotNull(savedStateHandle["subjectId"])

    private val _state = MutableStateFlow(SubjectDetailState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val semester = semesterManager.getActiveSemester()
            val subject  = subjectRepo.getById(subjectId)

            if (semester == null || subject == null) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }

            _state.update { it.copy(subject = subject, target = semester.targetAttendancePercent) }

            // Combine history + tasks reactively
            combine(
                attendanceRepo.observeSubjectHistory(semester.id, subjectId),
                taskRepo.observeForSubject(semester.id, subjectId),
            ) { history, tasks ->
                val present   = history.count { it.status == AttendanceStatus.PRESENT }
                val absent    = history.count { it.status == AttendanceStatus.ABSENT }
                val cancelled = history.count { it.status == AttendanceStatus.CANCELLED }
                val total     = present + absent
                val pct       = if (total == 0) 0f else present.toFloat() / total * 100f
                val target    = semester.targetAttendancePercent.toFloat()
                val warning   = target - semester.warningBufferPercent

                val canSkip = AttendanceCalculator.safeToSkip(present, absent, semester.targetAttendancePercent)
                val mustAttend = if (pct >= target || total == 0) 0 else {
                    val num = target * total - 100f * present
                    val den = 100f - target
                    if (den <= 0f) Int.MAX_VALUE else kotlin.math.ceil(num / den).toInt()
                }

                val trendPoints = buildTrendPoints(history)
                val weekBars    = buildWeekBars(history)

                val (lecStats, labStats) = AttendanceCalculator.computeSplit(
                    records       = history,
                    subjectType   = subject.type,
                    targetPercent = semester.targetAttendancePercent,
                    warningBuffer = semester.warningBufferPercent,
                )

                _state.update { st ->
                    st.copy(
                        isLoading      = false,
                        present        = present,
                        absent         = absent,
                        cancelled      = cancelled,
                        percentage     = pct,
                        isAtRisk       = total > 0 && pct < warning,
                        canSkip        = canSkip,
                        mustAttend     = mustAttend,
                        lecStats       = lecStats,
                        labStats       = labStats,
                        trendPoints    = trendPoints,
                        weekBars       = weekBars,
                        tasks          = tasks,
                        sessionHistory = history.sortedByDescending { it.date },
                    )
                }
            }.collect()
        }
    }

    // ── Chart builders ────────────────────────

    private fun buildTrendPoints(history: List<AttendanceEntity>): List<TrendPoint> {
        var runPresent = 0
        var runAbsent  = 0
        return history
            .filter { it.status != AttendanceStatus.CANCELLED }
            .sortedBy { it.date }
            .map { record ->
                if (record.status == AttendanceStatus.PRESENT) runPresent++
                else runAbsent++
                val total = runPresent + runAbsent
                TrendPoint(
                    date       = LocalDate.ofEpochDay(record.date),
                    percentage = if (total == 0) 0f else runPresent.toFloat() / total * 100f,
                )
            }
    }

    private fun buildWeekBars(history: List<AttendanceEntity>): List<WeekBar> {
        val weekField = WeekFields.of(Locale.getDefault()).weekOfYear()
        val grouped   = history
            .filter { it.status != AttendanceStatus.CANCELLED }
            .groupBy { LocalDate.ofEpochDay(it.date).get(weekField) }
            .entries
            .sortedBy { it.key }

        return grouped.mapIndexed { idx, (_, records) ->
            WeekBar(
                weekLabel = "W${idx + 1}",
                present   = records.count { it.status == AttendanceStatus.PRESENT },
                absent    = records.count { it.status == AttendanceStatus.ABSENT },
            )
        }
    }

    // ── Task form ─────────────────────────────

    fun openTaskForm()  = _state.update { it.copy(showTaskForm = true, taskForm = TaskFormState()) }
    fun closeTaskForm() = _state.update { it.copy(showTaskForm = false) }

    fun onTaskTitleChange(v: String)       = _state.update { it.copy(taskForm = it.taskForm.copy(title = v)) }
    fun onTaskTypeChange(v: TaskType)      = _state.update { it.copy(taskForm = it.taskForm.copy(type = v)) }
    fun onTaskPriorityChange(v: TaskPriority) = _state.update { it.copy(taskForm = it.taskForm.copy(priority = v)) }
    fun onTaskDueDateChange(v: LocalDate?) = _state.update { it.copy(taskForm = it.taskForm.copy(dueDate = v)) }
    fun onTaskNoteChange(v: String)        = _state.update { it.copy(taskForm = it.taskForm.copy(note = v)) }

    fun saveTask() {
        val form = _state.value.taskForm
        if (!form.canSave) return
        viewModelScope.launch {
            val semester = semesterManager.getActiveSemester() ?: return@launch
            taskRepo.insert(
                TaskEntity(
                    semesterId = semester.id,
                    subjectId  = subjectId,
                    title      = form.title.trim(),
                    type       = form.type,
                    priority   = form.priority,
                    dueDate    = form.dueDate?.toEpochDay(),
                    status     = TaskStatus.PENDING,
                    note       = form.note.ifBlank { null },
                )
            )
            _state.update { it.copy(showTaskForm = false) }
        }
    }

    fun updateTaskStatus(task: TaskEntity, status: TaskStatus) {
        viewModelScope.launch { taskRepo.updateStatus(task.id, status) }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch { taskRepo.deleteById(task.id) }
    }
}