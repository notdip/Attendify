package com.dip.attendify.ui.mark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dip.attendify.core.SemesterManager
import com.dip.attendify.data.entity.*
import com.dip.attendify.data.repository.AttendanceRepository
import com.dip.attendify.data.repository.SubjectRepository
import com.dip.attendify.data.repository.TimetableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// ─────────────────────────────────────────────
// Session model shown in the UI
// ─────────────────────────────────────────────

data class MarkSession(
    val attendanceId:  Int?              = null,   // null = not yet saved
    val subjectId:     Int,
    val subjectName:   String,
    val subjectColor:  String,
    val startSlotId:   Int,
    val endSlotId:     Int,
    val startTime:     String,
    val endTime:       String,
    val sessionType:   SessionType       = SessionType.REGULAR,
    val status:        AttendanceStatus? = null,   // null = unmarked
    val note:          String?           = null,
    val fromTimetable: Boolean           = true,
)

// ─────────────────────────────────────────────
// Available slot for Add Session sheet
// Only free or cancelled slots are shown
// ─────────────────────────────────────────────

data class AvailableSlot(
    val slotId:    Int,
    val startTime: String,
    val endTime:   String,
)

// ─────────────────────────────────────────────
// Add-session sheet state
// ─────────────────────────────────────────────

data class AddSessionForm(
    val subjectId:   Int?        = null,
    val startSlotId: Int?        = null,
    val endSlotId:   Int?        = null,
    val sessionType: SessionType = SessionType.PROXY,
    val note:        String      = "",
) {
    val canSave: Boolean
        get() = subjectId != null && startSlotId != null && endSlotId != null
}

// ─────────────────────────────────────────────
// Screen state
// ─────────────────────────────────────────────

data class MarkScreenState(
    val selectedDate:   LocalDate         = LocalDate.now(),
    val sessions:       List<MarkSession> = emptyList(),
    val isLoading:      Boolean           = true,
    val noSemester:     Boolean           = false,
    val semesterStart:  LocalDate?        = null,
    val semesterEnd:    LocalDate?        = null,
    val reloadTrigger:  Int               = 0,
    val showAddSheet:   Boolean           = false,
    val addForm:        AddSessionForm    = AddSessionForm(),
    val showNoteDialog: Int?              = null,
    val error:          String?           = null,
)

val MarkScreenState.allMarked: Boolean
    get() = sessions.isNotEmpty() && sessions.all { it.status != null }

// ─────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MarkViewModel @Inject constructor(
    private val semesterManager: SemesterManager,
    private val attendanceRepo:  AttendanceRepository,
    private val timetableRepo:   TimetableRepository,
    private val subjectRepo:     SubjectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MarkScreenState())
    val state = _state.asStateFlow()

    // Exposed for AddSessionSheet dropdowns
    private val _subjects = MutableStateFlow<List<SubjectEntity>>(emptyList())
    val subjects = _subjects.asStateFlow()

    private val _slots = MutableStateFlow<List<TimeSlotEntity>>(emptyList())
    val slots = _slots.asStateFlow()

    private val _availableSlots = MutableStateFlow<List<AvailableSlot>>(emptyList())
    val availableSlots = _availableSlots.asStateFlow()

    init {
        // Load subjects + slots first, THEN start the session flow.
        // Prevents the race where loadSessions runs before slots are populated.
        viewModelScope.launch {
            _subjects.value = subjectRepo.getAll()
            _slots.value    = timetableRepo.getAllSlots()

            _state
                .map { it.selectedDate to it.reloadTrigger }
                .distinctUntilChanged()
                .flatMapLatest { (date, _) -> buildSessionsFlow(date) }
                .collect { sessions ->
                    _state.update { it.copy(sessions = sessions, isLoading = false) }
                }
        }
    }

    // ── Date navigation ───────────────────────

    fun onDateChange(date: LocalDate) {
        val st    = _state.value
        val start = st.semesterStart
        val end   = st.semesterEnd
        val clamped = when {
            start != null && date.isBefore(start) -> start
            end   != null && date.isAfter(end)    -> end
            else -> date
        }
        _state.update { it.copy(selectedDate = clamped, isLoading = true) }
    }

    fun previousDay() {
        val st   = _state.value
        val prev = st.selectedDate.minusDays(1)
        if (st.semesterStart != null && prev.isBefore(st.semesterStart)) return
        onDateChange(prev)
    }

    fun nextDay() {
        val st   = _state.value
        val next = st.selectedDate.plusDays(1)
        if (st.semesterEnd != null && next.isAfter(st.semesterEnd)) return
        onDateChange(next)
    }

    // ── Mark status ───────────────────────────

    fun markSession(index: Int, status: AttendanceStatus) {
        val session = _state.value.sessions.getOrNull(index) ?: return
        viewModelScope.launch {
            val semester = semesterManager.getActiveSemester() ?: return@launch
            val date     = _state.value.selectedDate

            val existing = attendanceRepo.getExisting(
                semId       = semester.id,
                subjectId   = session.subjectId,
                date        = date.toEpochDay(),
                startSlotId = session.startSlotId,
                endSlotId   = session.endSlotId,
            )

            val entity = AttendanceEntity(
                id          = existing?.id ?: 0,
                semesterId  = semester.id,
                subjectId   = session.subjectId,
                date        = date.toEpochDay(),
                dayOfWeek   = date.dayOfWeek.value,
                startSlotId = session.startSlotId,
                endSlotId   = session.endSlotId,
                sessionType = session.sessionType,
                status      = status,
                note        = session.note,
            )

            if (existing != null) attendanceRepo.update(entity)
            else attendanceRepo.upsert(entity)

            // Optimistic UI update
            _state.update { st ->
                val updated = st.sessions.toMutableList()
                updated[index] = session.copy(
                    attendanceId = if (existing != null) existing.id else entity.id,
                    status       = status,
                )
                st.copy(sessions = updated)
            }
        }
    }

    /** Dismiss a timetable session for today — logs as CANCELLED. */
    fun dismissSession(index: Int) = markSession(index, AttendanceStatus.CANCELLED)

    /** Undo a cancelled session — deletes the CANCELLED record, restores to unmarked. */
    fun undoDismiss(index: Int) {
        val session = _state.value.sessions.getOrNull(index) ?: return
        val id      = session.attendanceId ?: return
        viewModelScope.launch {
            attendanceRepo.deleteById(id)
            _state.update { st ->
                val updated = st.sessions.toMutableList()
                updated[index] = session.copy(attendanceId = null, status = null)
                st.copy(sessions = updated)
            }
        }
    }

    // ── Notes ─────────────────────────────────

    fun clearAttendance(index: Int) {
        val session = _state.value.sessions.getOrNull(index) ?: return
        val id      = session.attendanceId ?: return
        viewModelScope.launch {
            attendanceRepo.deleteById(id)
            _state.update { st ->
                val updated = st.sessions.toMutableList()
                updated[index] = session.copy(attendanceId = null, status = null, note = null)
                st.copy(sessions = updated)
            }
        }
    }

    fun openNoteDialog(index: Int) = _state.update { it.copy(showNoteDialog = index) }
    fun closeNoteDialog()          = _state.update { it.copy(showNoteDialog = null) }

    fun saveNote(index: Int, note: String) {
        val session = _state.value.sessions.getOrNull(index) ?: return
        viewModelScope.launch {
            val semester = semesterManager.getActiveSemester() ?: return@launch
            val date     = _state.value.selectedDate
            attendanceRepo.getExisting(
                semId       = semester.id,
                subjectId   = session.subjectId,
                date        = date.toEpochDay(),
                startSlotId = session.startSlotId,
                endSlotId   = session.endSlotId,
            )?.let { attendanceRepo.update(it.copy(note = note.ifBlank { null })) }

            _state.update { st ->
                val updated = st.sessions.toMutableList()
                updated[index] = session.copy(note = note.ifBlank { null })
                st.copy(sessions = updated, showNoteDialog = null)
            }
        }
    }

    // ── Add session ───────────────────────────

    fun openAddSheet() {
        viewModelScope.launch {
            val semester = semesterManager.getActiveSemester()
            val allSlots = _slots.value
            val date     = _state.value.selectedDate

            // Read directly from DB so we always have the latest saved records,
            // not potentially stale in-memory sessions state
            val savedRecords = if (semester != null)
                attendanceRepo.getForDate(semester.id, date.toEpochDay())
            else emptyList()

            // Also include timetable entries for non-cancelled pending sessions
            val ttEntries = if (semester != null)
                timetableRepo.getEntriesForDay(semester.id, date.dayOfWeek.value)
            else emptyList()

            // Slots occupied by a saved present/absent record
            val savedOccupied = savedRecords
                .filter { it.status != com.dip.attendify.data.entity.AttendanceStatus.CANCELLED }
                .flatMap { record ->
                    val startIdx = allSlots.indexOfFirst { it.id == record.startSlotId }
                    val endIdx   = allSlots.indexOfFirst { it.id == record.endSlotId }
                    if (startIdx < 0) return@flatMap emptyList<Int>()
                    (startIdx..endIdx.coerceAtLeast(startIdx)).map { allSlots[it].id }
                }.toSet()

            // Slots occupied by a timetable entry that hasn't been cancelled
            val cancelledSlotIds = savedRecords
                .filter { it.status == com.dip.attendify.data.entity.AttendanceStatus.CANCELLED }
                .flatMap { record ->
                    val startIdx = allSlots.indexOfFirst { it.id == record.startSlotId }
                    val endIdx   = allSlots.indexOfFirst { it.id == record.endSlotId }
                    if (startIdx < 0) return@flatMap emptyList<Int>()
                    (startIdx..endIdx.coerceAtLeast(startIdx)).map { allSlots[it].id }
                }.toSet()

            val ttOccupied = ttEntries
                .filter { entry ->
                    // Only block if not cancelled
                    val key = Triple(entry.subjectId, entry.startSlotId, entry.endSlotId)
                    savedRecords.none {
                        it.subjectId == entry.subjectId &&
                                it.startSlotId == entry.startSlotId &&
                                it.endSlotId == entry.endSlotId &&
                                it.status == com.dip.attendify.data.entity.AttendanceStatus.CANCELLED
                    }
                }
                .flatMap { entry ->
                    val startIdx = allSlots.indexOfFirst { it.id == entry.startSlotId }
                    val endIdx   = allSlots.indexOfFirst { it.id == entry.endSlotId }
                    if (startIdx < 0) return@flatMap emptyList<Int>()
                    (startIdx..endIdx.coerceAtLeast(startIdx)).map { allSlots[it].id }
                }.toSet()

            val occupiedSlotIds = savedOccupied + ttOccupied

            // Available = slots that are free or were explicitly cancelled
            val available = allSlots
                .filter { it.id !in occupiedSlotIds }
                .map { slot -> AvailableSlot(slot.id, slot.startTime, slot.endTime) }

            _availableSlots.value = available
            _state.update { it.copy(showAddSheet = true, addForm = AddSessionForm()) }
        }
    }
    fun closeAddSheet() = _state.update { it.copy(showAddSheet = false) }

    fun onAddSubjectChange(id: Int)          = _state.update { it.copy(addForm = it.addForm.copy(subjectId = id)) }
    fun onAddStartSlotChange(id: Int)        = _state.update { it.copy(addForm = it.addForm.copy(startSlotId = id, endSlotId = id)) }
    fun onAddEndSlotChange(id: Int)          = _state.update { it.copy(addForm = it.addForm.copy(endSlotId = id)) }
    fun onAddNoteChange(n: String)           = _state.update { it.copy(addForm = it.addForm.copy(note = n)) }

    fun submitAddSession() {
        val form = _state.value.addForm
        if (!form.canSave) return
        viewModelScope.launch {
            val semester = semesterManager.getActiveSemester() ?: return@launch
            val date     = _state.value.selectedDate
            val semStart = LocalDate.ofEpochDay(semester.startDate)
            val semEnd   = LocalDate.ofEpochDay(semester.endDate)
            if (date.isBefore(semStart) || date.isAfter(semEnd)) {
                _state.update { it.copy(showAddSheet = false,
                    error = "Cannot add session outside semester dates") }
                return@launch
            }

            // Reuse any existing record for this exact slot (e.g. a CANCELLED
            // one from the same slot being reused for a makeup session)
            // instead of blindly inserting a duplicate row.
            val existing = attendanceRepo.getExisting(
                semId       = semester.id,
                subjectId   = form.subjectId!!,
                date        = date.toEpochDay(),
                startSlotId = form.startSlotId!!,
                endSlotId   = form.endSlotId!!,
            )

            val entity = AttendanceEntity(
                id          = existing?.id ?: 0,
                semesterId  = semester.id,
                subjectId   = form.subjectId,
                date        = date.toEpochDay(),
                dayOfWeek   = date.dayOfWeek.value,
                startSlotId = form.startSlotId,
                endSlotId   = form.endSlotId,
                sessionType = form.sessionType,
                status      = AttendanceStatus.PRESENT,
                note        = form.note.ifBlank { null },
            )

            if (existing != null) attendanceRepo.update(entity)
            else attendanceRepo.upsert(entity)

            // Increment reloadTrigger to force the flow to re-fire for the same date
            _state.update { it.copy(
                showAddSheet   = false,
                isLoading      = true,
                reloadTrigger  = it.reloadTrigger + 1,
            )}
        }
    }

    // ── Core: sessions flow ───────────────────

    private fun buildSessionsFlow(date: LocalDate): Flow<List<MarkSession>> {
        val semester = kotlinx.coroutines.runBlocking {
            semesterManager.getActiveSemester()
        }
        // Observe attendance records for this date reactively
        // Any insert/update/delete triggers a full session reload
        val attendanceFlow = if (semester != null)
            attendanceRepo.observeForDate(semester.id, date.toEpochDay())
        else
            kotlinx.coroutines.flow.flowOf(emptyList())

        return attendanceFlow.map { loadSessions(date) }
    }

    private fun refreshSessions() {
        viewModelScope.launch {
            val sessions = loadSessions(_state.value.selectedDate)
            _state.update { it.copy(sessions = sessions, isLoading = false) }
        }
    }

    private suspend fun loadSessions(date: LocalDate): List<MarkSession> {
        val semester = semesterManager.getActiveSemester()
        if (semester == null) {
            _state.update { it.copy(isLoading = false, noSemester = true) }
            return emptyList()
        }
        val semStart = LocalDate.ofEpochDay(semester.startDate)
        val semEnd   = LocalDate.ofEpochDay(semester.endDate)
        _state.update { st ->
            if (st.semesterStart == null) {
                // Clamp selectedDate to semester bounds on first load
                val clampedDate = when {
                    st.selectedDate.isBefore(semStart) -> semStart
                    st.selectedDate.isAfter(semEnd)    -> semEnd
                    else                               -> st.selectedDate
                }
                st.copy(
                    semesterStart = semStart,
                    semesterEnd   = semEnd,
                    selectedDate  = clampedDate,
                    noSemester    = false,
                )
            } else st
        }
        val subjects  = subjectRepo.getAll().associateBy { it.id }
        val slots     = timetableRepo.getAllSlots().associateBy { it.id }
        val dayOfWeek = date.dayOfWeek.value  // 1 = Mon … 7 = Sun

        val ttEntries = timetableRepo.getEntriesForDay(semester.id, dayOfWeek)
        val saved     = attendanceRepo.getForDate(semester.id, date.toEpochDay())
            .associateBy { Triple(it.subjectId, it.startSlotId, it.endSlotId) }

        val sessions = mutableListOf<MarkSession>()

        // 1. Timetable-seeded suggestions
        ttEntries.forEach { entry ->
            val subject   = subjects[entry.subjectId] ?: return@forEach
            val startSlot = slots[entry.startSlotId]  ?: return@forEach
            val endSlot   = slots[entry.endSlotId]    ?: return@forEach
            val record    = saved[Triple(entry.subjectId, entry.startSlotId, entry.endSlotId)]

            sessions.add(
                MarkSession(
                    attendanceId  = record?.id,
                    subjectId     = entry.subjectId,
                    subjectName   = subject.name,
                    subjectColor  = subject.colorHex,
                    startSlotId   = entry.startSlotId,
                    endSlotId     = entry.endSlotId,
                    startTime     = startSlot.startTime,
                    endTime       = endSlot.endTime,
                    sessionType   = record?.sessionType ?: SessionType.REGULAR,
                    status        = record?.status,
                    note          = record?.note,
                    fromTimetable = true,
                )
            )
        }

        // 2. Ad-hoc sessions saved for this date (not in timetable)
        val ttKeys = ttEntries.map { Triple(it.subjectId, it.startSlotId, it.endSlotId) }.toSet()
        saved.values
            .filter { Triple(it.subjectId, it.startSlotId, it.endSlotId) !in ttKeys }
            .forEach { record ->
                val subject   = subjects[record.subjectId] ?: return@forEach
                val startSlot = slots[record.startSlotId]  ?: return@forEach
                val endSlot   = slots[record.endSlotId]    ?: return@forEach

                sessions.add(
                    MarkSession(
                        attendanceId  = record.id,
                        subjectId     = record.subjectId,
                        subjectName   = subject.name,
                        subjectColor  = subject.colorHex,
                        startSlotId   = record.startSlotId,
                        endSlotId     = record.endSlotId,
                        startTime     = startSlot.startTime,
                        endTime       = endSlot.endTime,
                        sessionType   = record.sessionType,
                        status        = record.status,
                        note          = record.note,
                        fromTimetable = false,
                    )
                )
            }

        return sessions.sortedBy { slots[it.startSlotId]?.slotOrder ?: Int.MAX_VALUE }
    }
}