package com.dip.attendify.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dip.attendify.core.SemesterManager
import com.dip.attendify.data.entity.*
import com.dip.attendify.data.repository.SubjectRepository
import com.dip.attendify.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// ─────────────────────────────────────────────
// Filter state
// ─────────────────────────────────────────────

data class TaskFilter(
    val subjectId: Int?          = null,   // null = all subjects
    val type:      TaskType?     = null,   // null = all types
    val priority:  TaskPriority? = null,   // null = all priorities
    val showDone:  Boolean       = false,
)

// ─────────────────────────────────────────────
// Task with resolved subject name + color
// ─────────────────────────────────────────────

data class TaskItem(
    val task:          TaskEntity,
    val subjectName:   String,
    val subjectColor:  String,
)

// ─────────────────────────────────────────────
// Screen state
// ─────────────────────────────────────────────

data class TasksScreenState(
    val isLoading:     Boolean          = true,
    val allTasks:      List<TaskItem>   = emptyList(),
    val filteredTasks: List<TaskItem>   = emptyList(),
    val subjects:      List<SubjectEntity> = emptyList(),
    val filter:        TaskFilter       = TaskFilter(),
    val pendingCount:  Int              = 0,
    val overdueCount:  Int              = 0,
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val semesterManager: SemesterManager,
    private val taskRepo:        TaskRepository,
    private val subjectRepo:     SubjectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TasksScreenState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val semester = semesterManager.getActiveSemester()
            if (semester == null) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }

            combine(
                taskRepo.observeAllForSemester(semester.id),
                subjectRepo.observeAll(),
            ) { tasks, subjects ->
                val subjectMap = subjects.associateBy { it.id }
                val today      = LocalDate.now()

                val items = tasks.mapNotNull { task ->
                    val subject = subjectMap[task.subjectId] ?: return@mapNotNull null
                    TaskItem(
                        task         = task,
                        subjectName  = subject.name,
                        subjectColor = subject.colorHex,
                    )
                }

                val pending  = items.count { it.task.status != TaskStatus.DONE }
                val overdue  = items.count { item ->
                    item.task.status != TaskStatus.DONE &&
                            item.task.dueDate != null &&
                            LocalDate.ofEpochDay(item.task.dueDate).isBefore(today)
                }

                _state.update { st ->
                    val filtered = applyFilter(items, st.filter)
                    st.copy(
                        isLoading     = false,
                        allTasks      = items,
                        filteredTasks = filtered,
                        subjects      = subjects,
                        pendingCount  = pending,
                        overdueCount  = overdue,
                    )
                }
            }.collect()
        }
    }

    // ── Filter ────────────────────────────────

    fun setSubjectFilter(id: Int?)      = updateFilter { it.copy(subjectId = id) }
    fun setTypeFilter(type: TaskType?)  = updateFilter { it.copy(type = type) }
    fun setPriorityFilter(p: TaskPriority?) = updateFilter { it.copy(priority = p) }
    fun toggleShowDone()                = updateFilter { it.copy(showDone = !it.showDone) }
    fun clearFilters()                  = updateFilter { TaskFilter() }

    private fun updateFilter(transform: (TaskFilter) -> TaskFilter) {
        _state.update { st ->
            val newFilter = transform(st.filter)
            st.copy(
                filter        = newFilter,
                filteredTasks = applyFilter(st.allTasks, newFilter),
            )
        }
    }

    private fun applyFilter(items: List<TaskItem>, filter: TaskFilter): List<TaskItem> {
        val today = LocalDate.now()
        return items
            .filter { filter.subjectId == null || it.task.subjectId == filter.subjectId }
            .filter { filter.type     == null || it.task.type     == filter.type }
            .filter { filter.priority == null || it.task.priority == filter.priority }
            .filter { filter.showDone || it.task.status != TaskStatus.DONE }
            .sortedWith(
                compareBy<TaskItem> { it.task.status == TaskStatus.DONE }
                    .thenByDescending { it.task.priority }
                    .thenBy {
                        it.task.dueDate?.let { d -> LocalDate.ofEpochDay(d) }
                            ?: LocalDate.MAX
                    }
            )
    }

    // ── Actions ───────────────────────────────

    fun markDone(task: TaskEntity) {
        viewModelScope.launch {
            taskRepo.updateStatus(task.id, TaskStatus.DONE)
        }
    }

    fun undoDone(task: TaskEntity) {
        viewModelScope.launch {
            taskRepo.updateStatus(task.id, TaskStatus.PENDING)
        }
    }

    fun cycleStatus(task: TaskEntity) {
        val next = when (task.status) {
            TaskStatus.PENDING     -> TaskStatus.IN_PROGRESS
            TaskStatus.IN_PROGRESS -> TaskStatus.DONE
            TaskStatus.DONE        -> TaskStatus.PENDING
        }
        viewModelScope.launch { taskRepo.updateStatus(task.id, next) }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch { taskRepo.deleteById(task.id) }
    }
}