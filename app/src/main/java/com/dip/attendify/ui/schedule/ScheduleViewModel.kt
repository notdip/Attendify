package com.dip.attendify.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dip.attendify.core.SemesterManager
import com.dip.attendify.data.entity.*
import com.dip.attendify.data.repository.AttendanceRepository
import com.dip.attendify.data.repository.AcademicEventRepository
import com.dip.attendify.data.repository.SubjectRepository
import com.dip.attendify.data.repository.TimetableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

// ─────────────────────────────────────────────
// Timetable grid models
// ─────────────────────────────────────────────

data class GridCell(
    val subjectId:   Int,
    val subjectName: String,
    val shortName:   String,
    val colorHex:    String,
    val startSlotId: Int,
    val endSlotId:   Int,
    val spanSlots:   Int,
    val isContinuation: Boolean = false,
)

data class TimetableGridState(
    val slots:   List<TimeSlotEntity>                       = emptyList(),
    val grid:    Map<Pair<Int, Int>, GridCell>              = emptyMap(), // (day, slotId) -> cell
    val subjects: List<SubjectEntity>                      = emptyList(),
)

// ─────────────────────────────────────────────
// Resolved session for calendar day detail
// ─────────────────────────────────────────────

data class CalendarSession(
    val subjectName: String,
    val colorHex:    String,
    val startTime:   String,
    val endTime:     String,
    val status:      com.dip.attendify.data.entity.AttendanceStatus,
    val sessionType: com.dip.attendify.data.entity.SessionType,
)

// ─────────────────────────────────────────────
// Calendar models
// ─────────────────────────────────────────────

data class DayData(
    val date:          LocalDate,
    val attendancePct: Float?,          // null = no data
    val hasPresent:    Boolean,
    val hasAbsent:     Boolean,
    val events:        List<AcademicEventEntity> = emptyList(),
)

data class CalendarState(
    val displayMonth:      YearMonth         = YearMonth.now(),
    val days:              List<DayData>     = emptyList(),
    val selectedDate:      LocalDate?        = null,
    val selectedDayEvents: List<AcademicEventEntity> = emptyList(),
    val selectedDaySessions: List<CalendarSession> = emptyList(),
    val showAddEvent:      Boolean           = false,
    val eventForm:         EventFormState    = EventFormState(),
)

data class EventFormState(
    val title:   String           = "",
    val date:    LocalDate        = LocalDate.now(),
    val endDate: LocalDate?       = null,
    val type:    AcademicEventType = AcademicEventType.EXAM,
    val note:    String           = "",
) {
    val canSave: Boolean get() = title.isNotBlank()
}

// ─────────────────────────────────────────────
// Combined screen state
// ─────────────────────────────────────────────

data class ScheduleScreenState(
    val isLoading:  Boolean           = true,
    val timetable:  TimetableGridState = TimetableGridState(),
    val calendar:   CalendarState     = CalendarState(),
    val activeDayTab: Int             = 1,   // for timetable day tabs
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val semesterManager:  SemesterManager,
    private val timetableRepo:    TimetableRepository,
    private val subjectRepo:      SubjectRepository,
    private val attendanceRepo:   AttendanceRepository,
    private val eventRepo:        AcademicEventRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ScheduleScreenState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val semester = semesterManager.getActiveSemester()
            if (semester == null) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }

            // Timetable
            launch {
                combine(
                    timetableRepo.observeFullTimetable(semester.id),
                    subjectRepo.observeAll(),
                ) { entries, subjects ->
                    val slots      = timetableRepo.getAllSlots()
                    val subjectMap = subjects.associateBy { it.id }
                    val slotMap    = slots.associateBy { it.id }
                    buildGrid(entries, subjectMap, slotMap, slots)
                }.collect { gridState ->
                    _state.update { it.copy(timetable = gridState, isLoading = false) }
                }
            }

            // Calendar — reactive on events + attendance heatmap
            launch {
                combine(
                    attendanceRepo.observeHeatmapStats(semester.id),
                    eventRepo.observeAll(semester.id),
                ) { heatStats, events ->
                    val heatMap   = heatStats.associateBy { it.date }
                    val eventMap  = events.groupBy { it.date }
                    val month     = _state.value.calendar.displayMonth
                    buildCalendarDays(month, heatMap, eventMap)
                }.collect { days ->
                    _state.update { st ->
                        st.copy(calendar = st.calendar.copy(days = days))
                    }
                }
            }
        }
    }

    // ── Timetable ─────────────────────────────

    fun onDayTabChange(day: Int) =
        _state.update { it.copy(activeDayTab = day) }

    private fun buildGrid(
        entries:    List<TimetableEntryEntity>,
        subjectMap: Map<Int, SubjectEntity>,
        slotMap:    Map<Int, TimeSlotEntity>,
        slots:      List<TimeSlotEntity>,
    ): TimetableGridState {
        val grid = mutableMapOf<Pair<Int, Int>, GridCell>()

        entries.forEach { entry ->
            val subject  = subjectMap[entry.subjectId] ?: return@forEach
            val startIdx = slots.indexOfFirst { it.id == entry.startSlotId }
            val endIdx   = slots.indexOfFirst { it.id == entry.endSlotId }
            if (startIdx < 0) return@forEach
            val span = (endIdx - startIdx + 1).coerceAtLeast(1)

            for (i in 0 until span) {
                val slotId = slots[startIdx + i].id
                grid[Pair(entry.dayOfWeek, slotId)] = GridCell(
                    subjectId      = entry.subjectId,
                    subjectName    = subject.name,
                    shortName      = subject.shortName,
                    colorHex       = subject.colorHex,
                    startSlotId    = entry.startSlotId,
                    endSlotId      = entry.endSlotId,
                    spanSlots      = if (i == 0) span else 1,
                    isContinuation = i > 0,
                )
            }
        }

        return TimetableGridState(
            slots    = slots,
            grid     = grid,
            subjects = subjectMap.values.toList(),
        )
    }

    // ── Calendar ──────────────────────────────

    fun onMonthChange(month: YearMonth) {
        viewModelScope.launch {
            val semester = semesterManager.getActiveSemester() ?: return@launch
            val heatStats = attendanceRepo.observeHeatmapStats(semester.id).first()
            val events    = eventRepo.observeAll(semester.id).first()
            val heatMap   = heatStats.associateBy { it.date }
            val eventMap  = events.groupBy { it.date }
            val days      = buildCalendarDays(month, heatMap, eventMap)
            _state.update { st ->
                st.copy(calendar = st.calendar.copy(displayMonth = month, days = days))
            }
        }
    }

    fun onDateSelect(date: LocalDate) {
        val events = _state.value.calendar.days
            .find { it.date == date }?.events ?: emptyList()
        viewModelScope.launch {
            val semester = semesterManager.getActiveSemester()
            val calendarSessions = if (semester != null) {
                val records  = attendanceRepo.getForDate(semester.id, date.toEpochDay())
                    .filter { it.status != com.dip.attendify.data.entity.AttendanceStatus.CANCELLED }
                val subjects = subjectRepo.getAll().associateBy { it.id }
                val slots    = timetableRepo.getAllSlots().associateBy { it.id }
                records
                    .sortedBy { slots[it.startSlotId]?.slotOrder ?: Int.MAX_VALUE }
                    .mapNotNull { record ->
                        val subject   = subjects[record.subjectId] ?: return@mapNotNull null
                        val startSlot = slots[record.startSlotId]  ?: return@mapNotNull null
                        val endSlot   = slots[record.endSlotId]    ?: return@mapNotNull null
                        CalendarSession(
                            subjectName = subject.name,
                            colorHex    = subject.colorHex,
                            startTime   = startSlot.startTime,
                            endTime     = endSlot.endTime,
                            status      = record.status,
                            sessionType = record.sessionType,
                        )
                    }
            } else emptyList()
            _state.update { st ->
                st.copy(calendar = st.calendar.copy(
                    selectedDate         = date,
                    selectedDayEvents    = events,
                    selectedDaySessions  = calendarSessions,
                ))
            }
        }
    }

    fun openAddEvent(date: LocalDate) {
        _state.update { st ->
            st.copy(calendar = st.calendar.copy(
                showAddEvent = true,
                eventForm    = EventFormState(date = date),
            ))
        }
    }

    fun closeAddEvent() =
        _state.update { st -> st.copy(calendar = st.calendar.copy(showAddEvent = false)) }

    fun onEventTitleChange(v: String)       = updateEventForm { it.copy(title = v) }
    fun onEventDateChange(v: LocalDate)     = updateEventForm { it.copy(date = v) }
    fun onEventEndDateChange(v: LocalDate?) = updateEventForm { it.copy(endDate = v) }
    fun onEventTypeChange(v: AcademicEventType) = updateEventForm { it.copy(type = v) }
    fun onEventNoteChange(v: String)        = updateEventForm { it.copy(note = v) }

    private fun updateEventForm(transform: (EventFormState) -> EventFormState) =
        _state.update { st ->
            st.copy(calendar = st.calendar.copy(eventForm = transform(st.calendar.eventForm)))
        }

    fun saveEvent() {
        val form = _state.value.calendar.eventForm
        if (!form.canSave) return
        viewModelScope.launch {
            val semester = semesterManager.getActiveSemester() ?: return@launch
            eventRepo.insert(
                AcademicEventEntity(
                    semesterId = semester.id,
                    title      = form.title.trim(),
                    date       = form.date.toEpochDay(),
                    endDate    = form.endDate?.toEpochDay(),
                    type       = form.type,
                    note       = form.note.ifBlank { null },
                )
            )
            closeAddEvent()
        }
    }

    fun deleteEvent(event: AcademicEventEntity) {
        viewModelScope.launch { eventRepo.deleteById(event.id) }
    }

    private fun buildCalendarDays(
        month:    YearMonth,
        heatMap:  Map<Long, com.dip.attendify.data.db.dao.HeatStatRaw>,
        eventMap: Map<Long, List<AcademicEventEntity>>,
    ): List<DayData> {
        val firstDay  = month.atDay(1)
        val lastDay   = month.atEndOfMonth()
        return (0..lastDay.dayOfMonth - 1).map { offset ->
            val date    = firstDay.plusDays(offset.toLong())
            val epochDay = date.toEpochDay()
            val heat    = heatMap[epochDay]
            val total   = (heat?.present ?: 0) + (heat?.absent ?: 0)
            DayData(
                date          = date,
                attendancePct = if (total == 0) null
                else heat!!.present.toFloat() / total * 100f,
                hasPresent    = (heat?.present ?: 0) > 0,
                hasAbsent     = (heat?.absent  ?: 0) > 0,
                events        = eventMap[epochDay] ?: emptyList(),
            )
        }
    }
}